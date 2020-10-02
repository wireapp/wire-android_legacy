/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.waz.zclient.camera.controllers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.{Executors, ThreadFactory}

import android.content.Context
import android.content.res.Configuration
import android.graphics._
import android.hardware.Camera
import android.view.{OrientationEventListener, Surface, WindowManager}
import androidx.exifinterface.media.ExifInterface
import com.waz.bitmap.BitmapUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.RichFuture
import com.wire.signals.Signal
import com.waz.zclient.WireContext
import com.waz.zclient.camera.{CameraFacing, FlashMode}
import com.waz.zclient.core.logging.Logger
import com.waz.zclient.utils.DeprecationUtils.CameraWrap
import com.waz.zclient.utils.{AutoFocusCallbackDeprecation, Callback, CameraParamsWrapper, CameraSizeWrapper, CameraWrapper, DeprecationUtils, PictureCallbackDeprecated, ShutterCallbackDeprecated}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

class GlobalCameraController(cameraFactory: CameraFactory)(implicit cxt: WireContext)
  extends DerivedLogTag {
  
  implicit val cameraExecutionContext = new ExecutionContext {
    private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(r: Runnable): Thread = new Thread(r, "CAMERA")
    })


    override def reportFailure(cause: Throwable): Unit = Logger.error("GlobalCameraController", "Problem executing on Camera Thread.", cause)

    override def execute(runnable: Runnable): Unit = executor.submit(runnable)
  }

  //values protected for testing
  protected[camera] val camInfos = cameraFactory.getCameraInfos
  protected[camera] var currentCamera = Option.empty[WireCamera]
  protected[camera] var loadFuture = CancellableFuture.cancelled[(PreviewSize, Set[FlashMode])]()
  protected[camera] var currentCamInfo = camInfos.headOption //save this in global controller for consistency during the life of the app

  val currentFlashMode = Signal(FlashMode.OFF)

  val deviceOrientation = Signal(Orientation(0))

  def getCurrentCameraFacing = currentCamInfo.map(_.cameraFacing)

  /**
    * Cycles the currentCameraInfo to point to the next camera in the list of camera devices. This does NOT, however,
    * start the camera. The previos camera should be released and then openCamera() should be called again
    */
  def setNextCamera() = currentCamInfo.foreach(c => currentCamInfo = camInfos.lift((c.id + 1) % camInfos.size))

  /**
    * Returns a Future of a PreviewSize object representing the preview size that the camera preview will draw to.
    * This should be used to calculate the aspect ratio for re-sizing the texture
    */
  def openCamera(texture: SurfaceTexture, w: Int, h: Int) = {
    loadFuture.cancel()
    loadFuture = currentCamInfo.fold(CancellableFuture.cancelled[(PreviewSize, Set[FlashMode])]()) { info =>
      CancellableFuture {
        currentCamera = Some(cameraFactory(info, texture, w, h, cxt, deviceOrientation.currentValue.getOrElse(Orientation(0)), currentFlashMode.currentValue.getOrElse(FlashMode.OFF)))
        val previewSize = currentCamera.map(_.getPreviewSize).getOrElse(PreviewSize(0, 0))
        val flashModes = currentCamera.map(_.getSupportedFlashModes).getOrElse(Set.empty)
        (previewSize, flashModes)
      }
    }
    loadFuture
  }

  def takePicture(onShutter: => Unit) = Future {
    currentCamera match {
      case Some(c) => c.takePicture(onShutter)
      case _ => Future.failed(new RuntimeException("Take picture cannot be called while the camera is closed"))
    }
  }.flatten

  def releaseCamera(callback: Callback[Void]): Unit = releaseCamera().andThen {
    case _ => Option(callback).foreach(_.callback(null))
  }(Threading.Ui)

  def releaseCamera(): Future[Unit] = {
    loadFuture.cancel()
    Future {
      currentCamera.foreach { c =>
        c.release()
        currentCamera = None
      }
    }
  }

  def setFocusArea(touchRect: Rect, w: Int, h: Int) = Future {
    currentCamera match {
      case Some(c) => c.setFocusArea(touchRect, w, h)
      case _ => Future.failed(new RuntimeException("Can't set focus when camera is closed"))
    }
  }.flatten

  currentFlashMode.on(cameraExecutionContext)(fm => currentCamera.foreach(_.setFlashMode(fm)))

  deviceOrientation.on(cameraExecutionContext)(o => currentCamera.foreach(_.setOrientation(o)))

}

trait CameraFactory {
  def getCameraInfos: Seq[CameraInfo]
  def apply(info: CameraInfo, texture: SurfaceTexture, w: Int, h: Int, cxt: Context, devOrientation: Orientation, flashMode: FlashMode): WireCamera
}

class AndroidCameraFactory extends CameraFactory {
  override def apply(info: CameraInfo, texture: SurfaceTexture, w: Int, h: Int, cxt: Context, devOrientation: Orientation, flashMode: FlashMode) =
    new AndroidCamera(info, texture, w, h, cxt, devOrientation, flashMode)

  override def getCameraInfos = try {
    val info = DeprecationUtils.CameraInfoFactory()
    Seq.tabulate(DeprecationUtils.NUMBER_OF_CAMERAS) { i =>
      DeprecationUtils.getCameraInfo(i, info)
      CameraInfo(i, CameraFacing.getFacing(info.facing), info.orientation)
    }
  } catch {
    case e: Throwable =>
      Logger.warn("GlobalCameraController", "Failed to retrieve camera info - camera is likely unavailable", e)
      Seq.empty
  }
}

trait WireCamera {
  def getPreviewSize: PreviewSize

  def takePicture(shutter: => Unit): Future[Array[Byte]]

  def release(): Unit

  def setOrientation(o: Orientation): Unit

  def setFocusArea(touchRect: Rect, w: Int, h: Int): Future[Unit]

  def setFlashMode(fm: FlashMode): Unit

  def getSupportedFlashModes: Set[FlashMode]
}

class AndroidCamera(info: CameraInfo, texture: SurfaceTexture, w: Int, h: Int, cxt: Context, devOrientation: Orientation, flashMode: FlashMode) extends WireCamera {

  import WireCamera._

  private var camera = Option(Camera.open(info.id))
  private var previewSize: Option[PreviewSize] = None
  private var supportedFlashModes = Set.empty[FlashMode]

  /*
   * This part of the Wire software is heavily based on code posted in this Stack Overflow answer.
   * (http://stackoverflow.com/a/9888357/1751834)
   *
   * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
   * (http://creativecommons.org/licenses/by-sa/2.5)
   *
   * Contributors on StackOverflow:
   *  - user1035292 (http://stackoverflow.com/users/1035292/user1035292)
   *  - Tommy Visic (http://stackoverflow.com/users/710276/tommy-visic)
   */
  //TODO This assumes that if the device's natural orientation is not absolute 0, then it is 270.
  //This should be good for 99% of all devices, but might be good to fix up at some point.
  //Figuring out the devices natural orientation relative to absolute 0 is actually quite tricky...
  val naturalOrientation = {
    val config: Configuration = cxt.getResources.getConfiguration
    val defaultRotation: Int = cxt.getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager].getDefaultDisplay.getRotation

    defaultRotation match {
      case Surface.ROTATION_0 | Surface.ROTATION_180 if config.orientation == Configuration.ORIENTATION_LANDSCAPE => 270
      case Surface.ROTATION_90 | Surface.ROTATION_270 if config.orientation == Configuration.ORIENTATION_PORTRAIT => 270
      case _ => 0
    }
  }

  camera.foreach { c =>
    val pms = c.getParameters
    val wrapper = new CameraParamsWrapper(pms)
    c.setPreviewTexture(texture)
    val ps = getPreviewSize(wrapper, w, h)
    pms.setPreviewSize(ps.w.toInt, ps.h.toInt)
    previewSize = Some(ps)

    val pictureSize = getPictureSize(wrapper)
    pms.setPictureSize(pictureSize.width, pictureSize.height)

    pms.setRotation(getCameraRotation(devOrientation.orientation, info))
    c.setDisplayOrientation(getPreviewOrientation(naturalOrientation, info))

    supportedFlashModes = getSupportedFlashModesFromCamera
    if (supportedFlashModes.contains(flashMode)) pms.setFlashMode(flashMode.mode)
    else pms.setFlashMode(FlashMode.OFF.mode)

    if (clickToFocusSupported) setFocusMode(wrapper, FOCUS_MODE_AUTO)
    else setFocusMode(wrapper, FOCUS_MODE_CONTINUOUS_PICTURE)

    c.setParameters(pms)
    c.startPreview()
  }

  override def takePicture(shutter: => Unit): Future[Array[Byte]] = {
    val promise = Promise[Array[Byte]]()
    camera match {
      case Some(c) => try {
        c.takePicture(
          DeprecationUtils.shutterCallback(new ShutterCallbackDeprecated {
            override def onShutter(): Unit = Future(shutter)(Threading.Ui)
          }),
          null,
          DeprecationUtils.pictureCallback(new PictureCallbackDeprecated {

            override def onPictureTaken(data: Array[Byte], camera: CameraWrapper): Unit = {
              // Restart the preview as it gets stopped by camera.takePicture()
              c.startPreview()

              // Correct the orientation, if needed.
              val exif = new ExifInterface(new ByteArrayInputStream(data))
              val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

              val result = orientation match {
                case ExifInterface.ORIENTATION_NORMAL =>
                  data
                case ExifInterface.ORIENTATION_UNDEFINED =>
                  val corrected = BitmapUtils.fixOrientationForUndefined(BitmapFactory.decodeByteArray(data, 0, data.length), orientation)
                  generateOutputByteArray(corrected)
                case _ =>
                  val corrected = BitmapUtils.fixOrientation(BitmapFactory.decodeByteArray(data, 0, data.length), orientation)
                  generateOutputByteArray(corrected)
              }

              promise.success(result)
            }
          }))
      } catch {
        case e: Throwable => promise.failure(e)
      }
      case _ => promise.failure(new RuntimeException("Camera not available"))
    }
    promise.future
  }

 private def generateOutputByteArray(corrected: Bitmap) : Array[Byte] = {
   val output = new ByteArrayOutputStream()
   corrected.compress(Bitmap.CompressFormat.JPEG, 100, output)
   output.toByteArray
 }

  override def getPreviewSize = previewSize.getOrElse(PreviewSize(0, 0))

  override def release() = camera.foreach { c =>
    c.stopPreview()
    c.release()
    camera = None
  }

  override def setOrientation(o: Orientation) = DeprecationUtils.setParams(camera.orNull,
    new CameraWrap {
      def f(params: CameraParamsWrapper) = params.get.setRotation(getCameraRotation(o.orientation, info))
    })

  override def getSupportedFlashModes = supportedFlashModes

  //volatile because the camera will use the main thread to post callbacks to (since I'm using an executor and not a handler thread)
  @volatile private var settingFocus = false

  override def setFocusArea(touchRect: Rect, w: Int, h: Int) = {
    val promise = Promise[Unit]()
    camera match {
      case Some(c) =>
        if (touchRect.width == 0 || touchRect.height == 0) promise.success(())
        else if (clickToFocusSupported) {
          val focusArea = DeprecationUtils.Area(new Rect(
            touchRect.left * camCoordsRange / w - camCoordsOffset,
            touchRect.top * camCoordsRange / h - camCoordsOffset,
            touchRect.right * camCoordsRange / w - camCoordsOffset,
            touchRect.bottom * camCoordsRange / h - camCoordsOffset
          ), focusWeight)

          DeprecationUtils.setParams(camera.orNull, new CameraWrap {
            override def f(wrapper: CameraParamsWrapper): Unit = wrapper.get.setFocusAreas(List(focusArea).asJava)
          })
          if (!settingFocus) try {
            settingFocus = true

            DeprecationUtils.setAutoFocusCallback(c, new AutoFocusCallbackDeprecation {
              def onAutoFocus(s: java.lang.Boolean, cam: CameraWrapper): Unit = {
                if (!s) Logger.warn("GlobalCameraController", "Focus was unsuccessful - ignoring")
                promise.trySuccess(())
                settingFocus = false
              }
            })
          } catch {
            case e: Throwable => promise.failure(e)
          }
        }
        else promise.success(())
      case None => promise.success(())
    }
    promise.future
  }

  override def setFlashMode(fm: FlashMode): Unit = DeprecationUtils.setParams(camera.orNull, new CameraWrap {
    override def f(wrapper: CameraParamsWrapper): Unit = wrapper.get.setFlashMode(fm.mode)
  })

  private def getPreviewSize(params: CameraParamsWrapper, viewWidth: Int, viewHeight: Int) = {
    val targetRatio = params.get.getPictureSize.width.toDouble / params.get.getPictureSize.height.toDouble
    val targetHeight = Math.min(viewHeight, viewWidth)
    val sizes = params.get.getSupportedPreviewSizes.asScala.toVector.map(new CameraSizeWrapper(_))

    def byHeight(s: CameraSizeWrapper): Int = Math.abs(s.height - targetHeight)

    val filteredSizes = sizes.filterNot(s => Math.abs(s.width.toDouble / s.height.toDouble - targetRatio) > ASPECT_TOLERANCE)
    val optimalSize = if (filteredSizes.isEmpty) sizes.minBy(byHeight) else filteredSizes.minBy(byHeight)

    val (w, h) = (optimalSize.width, optimalSize.height)
    PreviewSize(w, h)
  }

  private def getPictureSize(pms: CameraParamsWrapper) =
    pms.get.getSupportedPictureSizes.asScala.map(new CameraSizeWrapper(_)).minBy { s =>
      val is16_9 = math.abs( (s.width.toDouble / s.height) - GlobalCameraController.Ratio_16_9 ) < 0.01
      val differenceToHeight = Math.abs(s.height - GlobalCameraController.MediumSize)
      (!is16_9, differenceToHeight) //Ordering[Boolean] considers false < true, so we want to flip the is16_9 check to give them preference
    }

  /**
    * activityRotation is relative to the natural orientation of the device, regardless of how the device is rotated.
    * That means if your holding the device rotated 90 clockwise, but the activity hasn't rotated because we fixed its
    * orientation, then the activityRotation is still 0, so that the preview is drawn the right way up.
    */
  private def getPreviewOrientation(activityRotation: Int, info: CameraInfo) =
    if (info.cameraFacing == CameraFacing.FRONT) (360 - ((info.fixedOrientation + activityRotation) % 360)) % 360
    else (info.fixedOrientation - activityRotation + 360) % 360

  private def getCameraRotation(deviceRotationDegrees: Int, info: CameraInfo) =
    if (info.cameraFacing == CameraFacing.FRONT) (info.fixedOrientation - deviceRotationDegrees + 360) % 360
    else (info.fixedOrientation + deviceRotationDegrees) % 360

  private def getSupportedFlashModesFromCamera = camera.fold(Set.empty[FlashMode]) { c =>
    Option(c.getParameters.getSupportedFlashModes).fold(Set.empty[FlashMode])(_.asScala.toSet.map(FlashMode.get))
  }

  private def clickToFocusSupported = camera.fold(false)(c => c.getParameters.getMaxNumFocusAreas > 0 && supportsFocusMode(new CameraParamsWrapper(c.getParameters), FOCUS_MODE_AUTO))

  private def supportsFocusMode(pms: CameraParamsWrapper, mode: String) = Option(pms.get.getSupportedFocusModes).fold(false)(_.contains(mode))

  private def setFocusMode(pms: CameraParamsWrapper, mode: String) = if (supportsFocusMode(pms, mode)) pms.get.setFocusMode(mode)
}

object GlobalCameraController {
  val Ratio_16_9: Double = 16.0 / 9.0
  val MediumSize = 1448
}

object WireCamera {
  val FOCUS_MODE_AUTO = "auto"
  val FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture"
  val ASPECT_TOLERANCE: Double = 0.1
  val camCoordsRange = 2000
  val camCoordsOffset = 1000
  val focusWeight = 1000
}

/**
  * Calculates the device's right-angle orientation based upon its rotation from its 'natural orientation',
  * which is always 0.
  */
trait Orientation {
  val orientation: Int
}

object Orientation {
  def apply(rot: Int): Orientation =
    if (rot == OrientationEventListener.ORIENTATION_UNKNOWN) Portrait_0
    else rot match {
      case r if (r > 315 && r <= 359) || (r >= 0 && r <= 45) => Portrait_0
      case r if r > 45 && r <= 135 => Landscape_90
      case r if r > 135 && r <= 225 => Portrait_180
      case r if r > 225 && r <= 315 => Landscape_270
      case _ =>
        Logger.warn("GlobalCameraController", "Unexpected orientation value: $rot")
        Portrait_0
    }
}

case object Portrait_0 extends Orientation { override val orientation: Int = 0 }
case object Landscape_90 extends Orientation { override val orientation: Int = 90 }
case object Portrait_180 extends Orientation { override val orientation: Int = 180 }
case object Landscape_270 extends Orientation { override val orientation: Int = 270 }

//CameraInfo.orientation is fixed for any given device, so we only need to store it once.
case class CameraInfo(id: Int, cameraFacing: CameraFacing, fixedOrientation: Int)
protected[camera] case class PreviewSize(w: Float, h: Float) {
  def hasSize = w != 0 && h != 0
}




