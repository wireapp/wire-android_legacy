/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.camera.views

import android.content.Context
import android.graphics.{Matrix, Rect, SurfaceTexture}
import android.os.Vibrator
import android.util.AttributeSet
import android.view._
import com.waz.ZLog
import com.waz.api.ImageAssetFactory
import com.waz.service.MediaManagerService
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.camera._
import com.waz.zclient.camera.controllers.{PreviewSize, Orientation, GlobalCameraController}
import com.waz.zclient.common.controllers.{CameraPermission, PermissionsController}
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._
import timber.log.Timber

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

class CameraPreviewTextureView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends TextureView(context, attrs, defStyleAttr) with ViewHelper with TextureView.SurfaceTextureListener {

  implicit val logTag = ZLog.logTagFor[CameraPreviewTextureView]

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  private val controller = inject[GlobalCameraController]
  private val vibrator = Option(inject[Vibrator])
  private val mediaManager = Option(inject[MediaManagerService]).flatMap(_.mediaManager)
  private val permissionsController = Option(inject[PermissionsController])

  private var currentTexture = Option.empty[(SurfaceTexture, Int, Int)]

  private var observer = Option.empty[CameraPreviewObserver]

  val orientationListener = returning {
    new OrientationEventListener(context) {
      override def onOrientationChanged(orientation: Int) =
        controller.deviceOrientation ! Orientation(orientation)
    }
  }(_.enable())

  setSurfaceTextureListener(this)

  def setObserver(observer: CameraPreviewObserver): Unit = {
    this.observer = Option(observer)
  }

  def takePicture() = controller.takePicture {
    val disableRepeat = -1
    vibrator.foreach(_.vibrate(getIntArray(R.array.camera).map(_.toLong), disableRepeat))
    mediaManager.foreach(_.playMedia(getResEntryName(R.raw.camera)))
  }.onComplete {
    case Success(data) => observer.foreach {
      _.onPictureTaken {
        if (getCameraFacing == CameraFacing.FRONT) ImageAssetFactory.getMirroredImageAsset(data)
        else ImageAssetFactory.getImageAsset(data)
      }
    }
    case Failure(_) =>
      observer.foreach(_.onCameraLoadingFailed())
  } (Threading.Ui)

  def getCameraFacing = controller.getCurrentCameraFacing.getOrElse(CameraFacing.BACK)

  def getNumberOfCameras = controller.camInfos.size

  def nextCamera() = {
    currentTexture.foreach {
      case (t, w, h) =>
        controller.releaseCamera()
        controller.setNextCamera()
        startLoading(t, w, h)
    }
  }

  def closeCamera() = controller.releaseCamera().andThen {
    case _ => observer.foreach(_.onCameraReleased())
  }(Threading.Ui)

  private def startLoading(texture: SurfaceTexture, width: Int, height: Int) = {
    controller.openCamera(texture, width, height).onComplete {
      case Success((previewSize, flashModes)) =>
        updateTextureMatrix((getWidth, getHeight), previewSize)
        observer.foreach(_.onCameraLoaded(flashModes.asJava))
      case Failure(ex : CancelException) =>
      case Failure(ex) =>
        Timber.w(ex, "Failed to open camera - camera is likely unavailable")
        observer.foreach(_.onCameraLoadingFailed())
    } (Threading.Ui)
  }

  override def onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = {
    permissionsController.foreach {
      _.requiring(Set(CameraPermission)) {
        currentTexture = Some((texture, width, height))
        startLoading(texture, width, height)
      }(R.string.camera_permissions_denied_title, R.string.camera_permissions_denied_message)
    }
  }

  override def onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = {
    controller.releaseCamera()
    controller.openCamera(surfaceTexture, width, height).onSuccess {
      case (previewSize, _) => updateTextureMatrix((getWidth, getHeight), previewSize)
    }(Threading.Ui)
  }

  override def onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = {
    currentTexture = None
    closeCamera().onComplete {
      case _ => texture.release()
    } (Threading.Ui)
    false
  }

  override def onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = {}

  def setFlashMode(fm: FlashMode) = controller.currentFlashMode ! fm

  def getCurrentFlashMode = controller.currentFlashMode.currentValue.get

  override def onTouchEvent(event: MotionEvent): Boolean = {
    if (event.getAction == MotionEvent.ACTION_UP) {
      val (x, y) = (event.getX, event.getY)
      val (touchMajor, touchMinor) = (event.getTouchMajor, event.getTouchMinor)
      val touchRect = new Rect(
        (x - touchMajor / 2).toInt,
        (y - touchMinor / 2).toInt,
        (x + touchMajor / 2).toInt,
        (y + touchMinor / 2).toInt)

      def ensureNonEmptyRect(rect: Rect) = {
        if (rect.width == 0) {
          rect.left = rect.left - 1
          rect.right = rect.right + 1
        }

        if (rect.height == 0) {
          rect.top = rect.top - 1
          rect.bottom = rect.bottom + 1
        }
      }
      ensureNonEmptyRect(touchRect)

      currentTexture.foreach {
        case (_, w, h) =>
          observer.foreach(_.onFocusBegin(touchRect))
          controller.setFocusArea(touchRect, w, h).onComplete {
            case _ => observer.foreach(_.onFocusComplete())
          }(Threading.Ui)
      }
    }
    true
  }

  /*
   * This part (the method updateTextureMatrix) of the Wire software is based heavily off of code posted in this
   * Stack Overflow answer.
   * (http://stackoverflow.com/a/21630665/1751834)
   *
   * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
   * (http://creativecommons.org/licenses/by-sa/2.5)
   *
   * Contributors on StackOverflow:
   *  - Ruslan Yanchyshyn (http://stackoverflow.com/users/779140/ruslan-yanchyshyn)
   */
  private def updateTextureMatrix(viewSize: (Int, Int), previewSize: PreviewSize): Unit = if (previewSize.hasSize) {
    //Assuming that the view width and the surface width are the same - not sure if this will always be true
    val (viewWidth, viewHeight) = (getWidth.toFloat, getHeight.toFloat)

    val ratioSurface = viewWidth / viewHeight
    val ratioPreview = previewSize.h / previewSize.w //We always asume the 'activity' is portrait relative to the top of the device.

    val (stretchedWidth, stretchedHeight) =
      if (ratioSurface > ratioPreview)
        (viewWidth, viewWidth / ratioPreview)
      else
        (ratioPreview * viewHeight, viewHeight)

    val matrix = new Matrix()
    matrix.setScale(stretchedWidth / viewWidth, stretchedHeight / viewHeight)
    matrix.postTranslate((viewWidth - stretchedWidth) / 2, (viewHeight - stretchedHeight) / 2)
    setTransform(matrix)
  }
}
