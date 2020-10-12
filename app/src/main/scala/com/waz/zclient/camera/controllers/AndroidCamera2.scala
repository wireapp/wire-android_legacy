package com.waz.zclient.camera.controllers

import java.io.{ByteArrayOutputStream, Closeable}
import java.util.concurrent.ArrayBlockingQueue

import android.content.Context
import android.graphics._
import android.hardware.camera2._
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader.OnImageAvailableListener
import android.media.{ExifInterface, Image, ImageReader}
import android.os.{Handler, HandlerThread}
import android.view.Surface
import com.waz.bitmap.BitmapUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.camera.FlashMode

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

case class Orientation(orientation: Int)

case class CameraData(cameraId: String, facing: Int)

case class CombinedCaptureResult(image: Image,
                                 metadata: CaptureResult,
                                 orientation: Int,
                                 format: Int
                                ) extends Closeable {
  override def close(): Unit = image.close()
}

class AndroidCamera2(cameraData: CameraData,
                     cxt: Context,
                     width: Int,
                     height: Int,
                     flashMode: FlashMode,
                     texture: SurfaceTexture)
  extends WireCamera with DerivedLogTag {

  import ExifInterface._
  import WireCamera._
  import com.waz.zclient.log.LogUI._

  private lazy val cameraThread = returning(new HandlerThread("CameraThread")) {
    _.start()
  }
  private lazy val imageReaderThread = returning(new HandlerThread("ImageReaderThread")) {
    _.start()
  }

  private val cameraHandler = new Handler(cameraThread.getLooper)
  private val imageReaderHandler = new Handler(imageReaderThread.getLooper)

  private val cameraManager = cxt.getApplicationContext.getSystemService(Context.CAMERA_SERVICE).asInstanceOf[CameraManager]
  private val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraData.cameraId)

  private lazy val largestSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    .getOutputSizes(ImageFormat.JPEG)
    .maxBy { size => size.getHeight * size.getWidth }

  private lazy val imageReader: ImageReader = returning(
    ImageReader.newInstance(
      largestSize.getWidth, largestSize.getHeight,
      ImageFormat.JPEG,
      ImageBufferSize
    )
  ) {
    _.setOnImageAvailableListener(null, imageReaderHandler)
  }

  private var cameraRequest: Option[CaptureRequest.Builder] = None
  private var cameraSession: Option[CameraCaptureSession] = None
  private var camera: Option[CameraDevice] = None
  private var orientation: Orientation = AndroidCamera2.DEFAULT_ORIENTATION

  private def getSupportedFlashMode(mode: FlashMode): Int =
    if (getSupportedFlashModes(mode)) mode.mode else FlashMode.OFF.mode

  def initCamera(): Unit = {
    openCamera(cameraManager, cameraData.cameraId, cameraHandler)

    texture.setDefaultBufferSize(getPreviewSize.w.toInt, getPreviewSize.h.toInt)

    val surface = new Surface(texture)
    val targets = List(surface, imageReader.getSurface)

    camera.foreach { device: CameraDevice =>
      cameraRequest = returning(Option(device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW))) { _.foreach { request =>
        request.set(requestKey(CaptureRequest.CONTROL_MODE), CameraMetadata.CONTROL_MODE_AUTO)
        request.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        request.set(requestKey(CaptureRequest.FLASH_MODE), getSupportedFlashMode(flashMode))
        request.addTarget(surface)
      } }
      createCameraSession(targets, device)
    }
  }

  private def openCamera(cameraManager: CameraManager, id: String, cameraHandler: Handler): Unit = {
    try {
      cameraManager.openCamera(id, new CameraDevice.StateCallback {
        override def onOpened(device: CameraDevice): Unit = {
          camera = Some(device)
        }

        override def onDisconnected(device: CameraDevice): Unit = {
          release()
          camera = None
        }

        override def onError(camera: CameraDevice, errorCode: Int): Unit = {
          val msg = errorCode match {
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE      => "Fatal (device)"
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED    => "Device policy"
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE      => "Camera in use"
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE     => "Fatal (service)"
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE => "Maximum cameras in use"
            case _ => "Unknown"
          }
          error(l"Camera  error: ($errorCode) $msg")
        }
      }, cameraHandler)
    } catch {
      case ex: CameraAccessException    => error(l"Camera access error when opening camera: ", ex)
      case ex: SecurityException        => error(l"The app has no permission to access the camera", ex)
      case ex: IllegalArgumentException => error(l"Opening the camera failed", ex)
    }
  }

  private def createCameraSession(targets: List[Surface], camera: CameraDevice): Unit = {
    try {
      camera.createCaptureSession(targets.asJava, new CameraCaptureSession.StateCallback {
        override def onConfigured(session: CameraCaptureSession): Unit = {
          cameraSession = Some(session)
          cameraRequest.foreach { request =>
            request.set(requestKey(CaptureRequest.CONTROL_MODE), CameraMetadata.CONTROL_MODE_AUTO)
            request.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            session.setRepeatingRequest(request.build(), null, cameraHandler)
          }
        }

        override def onConfigureFailed(session: CameraCaptureSession): Unit = {
          verbose(l"onConfigureFailed($session)")
          //Release open session
          Try(session.abortCaptures()).recover {
            case ex: CameraAccessException => error(l"Camera access error when creating camera session", ex)
            case ex: IllegalStateException => error(l"The session appears to longer be active", ex)
          }
          session.close()
          cameraSession = None
        }
      }, cameraHandler)
    } catch {
      case ex: CameraAccessException => error(l"Camera access error when creating camera session", ex)
      case ex: IllegalStateException => error(l"The session appears to longer be active", ex)
    }
  }

  override def getPreviewSize: PreviewSize = {
    val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    // We cast here to Long to ensure the multiplications won't overflow.
    val largest = map.getOutputSizes(ImageFormat.JPEG).maxBy(s => s.getWidth.toLong * s.getHeight.toLong)
    val allSizes = map.getOutputSizes(classOf[SurfaceTexture])

    // We filter out sizes with the ratio too different from the largest one.
    val targetRatio = largest.getWidth.toDouble / largest.getHeight.toDouble
    val validSizes = allSizes.filterNot(s => Math.abs(s.getWidth.toDouble / s.getHeight.toDouble - targetRatio) > AndroidCamera2.ASPECT_TOLERANCE)

    // We look for the size with the height most similar to the size of the view,
    // assuming the height is always the smaller one of the two dimensions.
    val targetHeight = Math.min(height, width)
    val chosenSize = (if (validSizes.isEmpty) allSizes else validSizes).minBy(s => Math.abs(s.getHeight - targetHeight))

    PreviewSize(chosenSize.getWidth, chosenSize.getHeight)
  }

  override def takePicture(shutter: => Unit): Future[Array[Byte]] = {
    val promise = Promise[Array[Byte]]
    try {
      takePhoto().onComplete {
        case Success(photoResult) =>
          val buffer = photoResult.image.getPlanes.head.getBuffer
          val data = new Array[Byte](buffer.remaining())
          buffer.get(data)
          // Correct the orientation, if needed.
          val result = photoResult.orientation match {
            case ORIENTATION_NORMAL => data
            case ORIENTATION_UNDEFINED =>
              val corrected = BitmapUtils.fixOrientationForUndefined(BitmapFactory.decodeByteArray(data, 0, data.length), photoResult.orientation)
              generateOutputByteArray(corrected)
            case _ =>
              val corrected = BitmapUtils.fixOrientation(BitmapFactory.decodeByteArray(data, 0, data.length), photoResult.orientation)
              generateOutputByteArray(corrected)
          }
          promise.success(result)
        case Failure(exception) => promise.failure(exception)
      }(Threading.Ui)
    } catch {
      case ex: CameraAccessException =>
        error(l"Camera access error when taking a photo: ", ex)
        promise.failure(ex)
    }
    promise.future
  }

  private def generateOutputByteArray(corrected: Bitmap): Array[Byte] =
    returning(new ByteArrayOutputStream()) {
      corrected.compress(Bitmap.CompressFormat.JPEG, 100, _)
    }.toByteArray

  private def takePhoto(): Future[CombinedCaptureResult] = {
    val promise = Promise[CombinedCaptureResult]()
    val imageQueue = new ArrayBlockingQueue[Image](ImageBufferSize)
    imageReader.setOnImageAvailableListener(new OnImageAvailableListener {
      override def onImageAvailable(reader: ImageReader): Unit = {
        val image = reader.acquireNextImage()
        imageQueue.add(image)
      }
    }, imageReaderHandler)

    cameraSession.foreach { session: CameraCaptureSession =>
      val captureRequest = returning(session.getDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)) {
        _.addTarget(imageReader.getSurface)
      }
      session.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback {
        private def computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = (rotationDegrees, mirrored) match {
          case (0, false)    => ORIENTATION_NORMAL
          case (0, true)     => ORIENTATION_FLIP_HORIZONTAL
          case (90, false)   => ORIENTATION_ROTATE_90
          case (90, true)    => ORIENTATION_TRANSPOSE
          case (180, false)  => ORIENTATION_ROTATE_180
          case (180, true)   => ORIENTATION_FLIP_VERTICAL
          case (270, false)  => ORIENTATION_TRANSVERSE
          case (270, true)   => ORIENTATION_ROTATE_270
          case _             => ORIENTATION_UNDEFINED
        }

        private def computeRelativeOrientation(orientation: Int, mirrored: Boolean): Int =
          if (orientation != android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            //Round the orientation to the nearest 90
            var deviceOrientation = (orientation + 45) / 90 * 90

            //if front-facing flip the image
            if (mirrored) deviceOrientation = -deviceOrientation

            //Find the relative orientation between the camera sensor and the device orientation
            (sensorOrientation + deviceOrientation + 360) % 360
          } else 0

        override def onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult): Unit = {
          super.onCaptureCompleted(session, request, result)
          val timeoutExc = new RuntimeException("Image de-queuing took too long")
          val timeoutRunnable = new Runnable {
            override def run(): Unit = promise.failure(timeoutExc)
          }
          imageReaderHandler.postDelayed(timeoutRunnable, ImageCaptureTimeoutMillis)

          val image = imageQueue.take()

          imageReaderHandler.removeCallbacks(timeoutRunnable)
          imageReader.setOnImageAvailableListener(null, null)

          while (imageQueue.size > 0) {
            imageQueue.take().close()
          }
          val mirrored = getCurrentCameraFacing == CameraMetadata.LENS_FACING_FRONT
          val relativeOrientation = computeRelativeOrientation(orientation.orientation, mirrored)
          val exifOrientation = computeExifOrientation(relativeOrientation, mirrored)
          promise.success(CombinedCaptureResult(image, result, exifOrientation, imageReader.getImageFormat))
        }
      }, cameraHandler)
    }
    promise.future
  }

  override def release(): Unit = {
    imageReader.discardFreeBuffers()
    imageReader.close()
    cameraSession.foreach { session =>
      session.stopRepeating()
      session.close()
    }
    camera.foreach(_.close())
    cameraRequest = None

    imageReaderThread.quitSafely()
    cameraThread.quitSafely()
  }

  override def setFocusArea(touchRect: Rect, w: Int, h: Int): Future[Unit] = {
    val promise = Promise[Unit]
    try {
      val touchRect = new Rect(w - 100, h - 100, w + 100, h + 100)
      (cameraSession, cameraRequest) match {
        case (Some(session), Some(requestBuilder)) =>
          session.stopRepeating()
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_OFF)
          session.capture(requestBuilder.build(), null, cameraHandler)

          val focusAreas = Array(new MeteringRectangle(touchRect, MeteringRectangle.METERING_WEIGHT_DONT_CARE))
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_REGIONS), focusAreas)
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_MODE), CameraMetadata.CONTROL_MODE_AUTO)
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_AUTO)
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), CameraMetadata.CONTROL_AF_TRIGGER_START)

          val callback = new CameraCaptureSession.CaptureCallback {
            override def onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult): Unit = {
              super.onCaptureCompleted(session, request, result)
              if (request.getTag == "FOCUS_TAG") {
                requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), null)
                session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
              }
            }
          }
          requestBuilder.setTag("FOCUS_TAG")
          session.capture(requestBuilder.build(), callback, cameraHandler)
          promise.success(Unit)
        case _ =>
      }
    } catch {
      case ex: CameraAccessException => error(l"Camera access error when creating camera session", ex); promise.failure(ex)
      case ex: IllegalStateException => error(l"The session appears to longer be active", ex); promise.failure(ex)
    }
    promise.future
  }

  override def setFlashMode(fm: FlashMode): Unit = {
    cameraSession.foreach { session =>
      cameraRequest.foreach { request =>
        fm match {
          case FlashMode.AUTO =>
            request.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
          case FlashMode.OFF =>
            request.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON)
            request.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_OFF)
          case FlashMode.ON | FlashMode.TORCH =>
            request.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON)
            request.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_TORCH)
        }
        session.setRepeatingRequest(request.build(), null, cameraHandler)
      }
    }
  }

  override def getSupportedFlashModes: Set[FlashMode] =
    Option(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES))
      .fold(Set.empty[FlashMode])(_.map(FlashMode.get).toSet)

  override def setOrientation(o: Orientation): Unit = {
    orientation = o
  }

  override def getCurrentCameraFacing: Int = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

  private def requestKey[T](key: CaptureRequest.Key[T]): CaptureRequest.Key[Any] =
    key.asInstanceOf[CaptureRequest.Key[Any]]
}

object AndroidCamera2 {
  final val DEFAULT_ORIENTATION: Orientation = Orientation(0)
  final val ASPECT_TOLERANCE: Double = 0.1
}

