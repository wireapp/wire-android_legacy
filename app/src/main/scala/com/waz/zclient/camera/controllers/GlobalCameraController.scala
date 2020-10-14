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

import java.util.concurrent.{Executors, ThreadFactory}

import android.content.Context
import android.graphics._
import android.hardware.camera2.{CameraCharacteristics, CameraManager, CameraMetadata}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.zclient.WireContext
import com.waz.zclient.camera.{CameraFacing, FlashMode}
import com.waz.zclient.utils.Callback
import com.wire.signals.{CancellableFuture, Signal, SourceSignal}

import scala.concurrent.{ExecutionContext, Future}

class GlobalCameraController(cameraFactory: CameraFactory)(implicit cxt: WireContext)
  extends DerivedLogTag {

  import com.waz.zclient.log.LogUI._

  implicit val cameraExecutionContext: ExecutionContext = new ExecutionContext {
    private val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(r: Runnable): Thread = new Thread(r, "CAMERA")
    })

    override def reportFailure(cause: Throwable): Unit = error(l"Problem executing on Camera Thread.", cause)

    override def execute(runnable: Runnable): Unit = executor.submit(runnable)
  }

  private val cameraManager = cxt.getApplicationContext.getSystemService(Context.CAMERA_SERVICE).asInstanceOf[CameraManager]

  //values protected for testing
  protected[camera] val availableCameraData = cameraFactory.getAvailableCameraData(cameraManager)
  protected[camera] var currentCamera = Option.empty[WireCamera]
  protected[camera] var loadFuture = CancellableFuture.cancelled[(PreviewSize, Set[FlashMode])]()
  protected[camera] var currentCameraData = availableCameraData.headOption //save this in global controller for consistency during the life of the app

  val currentFlashMode: SourceSignal[FlashMode] = Signal(FlashMode.OFF)
  val deviceOrientation: SourceSignal[Orientation] = Signal(Orientation(0))

  def getCurrentCameraFacing: Option[Int] = currentCamera.map(_.getCurrentCameraFacing)

  /**
   * Cycles the currentCameraInfo to point to the next camera in the list of camera devices. This does NOT, however,
   * start the camera. The previous camera should be released and then openCamera() should be called again
   */

  def goToNextCamera(): Unit =
    currentCamera.foreach { camera =>
      camera.release()
      val index = availableCameraData.indexWhere(_.facing != camera.getCurrentCameraFacing)
      currentCameraData = availableCameraData.lift(index)
    }

  /**
   * Returns a Future of a PreviewSize object representing the preview size that the camera preview will draw to.
   * This should be used to calculate the aspect ratio for re-sizing the texture
   */
  def openCamera(texture: SurfaceTexture, w: Int, h: Int): CancellableFuture[(PreviewSize, Set[FlashMode])] = {
    loadFuture.cancel()
    loadFuture = currentCameraData.fold(CancellableFuture.cancelled[(PreviewSize, Set[FlashMode])]()) { data =>
      CancellableFuture {
        currentCamera = Some(
          cameraFactory(
            data, texture, cxt, w, h,
            deviceOrientation.currentValue.getOrElse(AndroidCamera2.DEFAULT_ORIENTATION),
            currentFlashMode.currentValue.getOrElse(FlashMode.OFF)
          )
        )
        val previewSize = currentCamera.map(_.getPreviewSize).getOrElse(PreviewSize(0, 0))
        val flashModes = currentCamera.map(_.getSupportedFlashModes).getOrElse(Set.empty)
        (previewSize, flashModes)
      }
    }
    loadFuture
  }

  def takePicture(onShutter: => Unit): Future[Array[Byte]] = currentCamera match {
    case Some(c) => c.takePicture(onShutter)
    case _ => Future.failed(new RuntimeException("Take picture cannot be called while the camera is closed"))
  }

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

  def setFocusArea(touchRect: Rect, w: Int, h: Int): Future[Unit] = currentCamera match {
    case Some(c) => c.setFocusArea(touchRect, w, h)
    case _ => Future.failed(new RuntimeException("Can't set focus when camera is closed"))
  }

  currentFlashMode.on(cameraExecutionContext)(fm => currentCamera.foreach(_.setFlashMode(fm)))
  deviceOrientation.on(cameraExecutionContext)(o => currentCamera.foreach(_.setOrientation(o)))

}

trait CameraFactory {
  def getAvailableCameraData(cameraManager: CameraManager): Seq[CameraData]

  def apply(data: CameraData, texture: SurfaceTexture, cxt: Context, width: Int, height: Int, devOrientation: Orientation, flashMode: FlashMode): WireCamera
}

class AndroidCameraFactory extends CameraFactory with DerivedLogTag {

  import com.waz.zclient.log.LogUI._

  override def apply(data: CameraData, texture: SurfaceTexture, cxt: Context, width: Int, height: Int, devOrientation: Orientation, flashMode: FlashMode): WireCamera = {
    val camera = new AndroidCamera2(data, cxt, width, height, flashMode, texture)
    camera.initCamera()
    camera.asInstanceOf[WireCamera]
  }

  override def getAvailableCameraData(cameraManager: CameraManager): Seq[CameraData] = try {
    val cameraIds = cameraManager.getCameraIdList.filter { id =>
      val characteristics = cameraManager.getCameraCharacteristics(id)
      Option(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))
        .exists(_.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE))
    }
    cameraIds.map { cameraId =>
      val facing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
      CameraData(cameraId, facing)
    }
  } catch {
    case e: Throwable =>
      error(l"Failed to retrieve camera info - camera is likely unavailable", e)
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

  def getCurrentCameraFacing: Int
}

object WireCamera {
  val ImageBufferSize = 3
  val ImageCaptureTimeoutMillis = 5000
}

//CameraInfo.orientation is fixed for any given device, so we only need to store it once.
case class CameraInfo(id: Int, cameraFacing: CameraFacing, fixedOrientation: Int)

protected[camera] case class PreviewSize(w: Float, h: Float) {
  def hasSize = w != 0 && h != 0
}




