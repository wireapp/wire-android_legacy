package com.waz.zclient.camera.controllers

import java.io.{ByteArrayOutputStream, Closeable}
import java.util.concurrent.{ArrayBlockingQueue, Executor}

import android.content.Context
import android.graphics._
import android.hardware.camera2._
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.{OutputConfiguration, SessionConfiguration}
import android.media.ImageReader.OnImageAvailableListener
import android.media.{ExifInterface, Image, ImageReader}
import android.os.{Build, Handler, HandlerThread}
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

  import AndroidCamera2._
  import ExifInterface._
  import WireCamera._
  import com.waz.zclient.log.LogUI._

  private var updatedFlash: FlashMode = flashMode

  private lazy val cameraThread = returning(new HandlerThread("CameraThread")) {
    _.start()
  }
  private lazy val imageReaderThread = returning(new HandlerThread("ImageReaderThread")) {
    _.start()
  }

  private lazy val cameraHandler = new Handler(cameraThread.getLooper)
  private lazy val imageReaderHandler = new Handler(imageReaderThread.getLooper)

  private lazy val cameraManager = cxt.getApplicationContext.getSystemService(Context.CAMERA_SERVICE).asInstanceOf[CameraManager]
  private lazy val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraData.cameraId)

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
  private var orientation: Orientation = DEFAULT_ORIENTATION

  def initCamera(): Unit = {
    openCamera(cameraManager, cameraData.cameraId, cameraHandler)

    texture.setDefaultBufferSize(getPreviewSize.w.toInt, getPreviewSize.h.toInt)

    val surface = new Surface(texture)
    val targets = List(surface, imageReader.getSurface)

    camera.foreach { device: CameraDevice =>
      cameraRequest = returning(Option(device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW))) {
        _.foreach { request =>
          request.addTarget(surface)
        }
      }
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
      case ex: SecurityException          => error(l"The app has no permission to access the camera", ex)
      case ex: CameraAccessException      => error(l"Camera access error when opening camera: ", ex)
      case ex: IllegalArgumentException   => error(l"Opening the camera failed", ex)
    }
  }

  private def createCameraSession(targets: List[Surface], camera: CameraDevice): Unit =
    try {
      camera.createCaptureSession(new SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        targets.map(new OutputConfiguration(_)).asJava,
        new Executor { // TODO: maybe this executor should be somehow better tied to cameraHandler?
          override def execute(command: Runnable): Unit = Threading.Background.execute(command)
        },
        new CameraCaptureSession.StateCallback {
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
        }
      ))
    } catch {
      case ex: CameraAccessException => error(l"Camera access error when creating camera session", ex)
      case ex: IllegalStateException => error(l"The session appears to longer be active", ex)
    }

  override def getPreviewSize: PreviewSize = {
    val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    // We cast here to Long to ensure the multiplications won't overflow.
    val largest = map.getOutputSizes(ImageFormat.JPEG).maxBy(s => s.getWidth.toLong * s.getHeight.toLong)
    val allSizes = map.getOutputSizes(classOf[SurfaceTexture])

    // We filter out sizes with the ratio too different from the largest one.
    val targetRatio = largest.getWidth.toDouble / largest.getHeight.toDouble
    val validSizes = allSizes.filterNot(s => Math.abs(s.getWidth.toDouble / s.getHeight.toDouble - targetRatio) > ASPECT_TOLERANCE)

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
          photoResult.close()
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
      val captureRequest = returning(session.getDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)) { request =>
        request.addTarget(imageReader.getSurface)
      }
      determineFlash(captureRequest)
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
            val sensorOrientation = Option(cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)).map(_.intValue()).getOrElse(0)

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

          updatePreview()

          promise.success(CombinedCaptureResult(image, result, exifOrientation, imageReader.getImageFormat))
        }
      }, cameraHandler)
    }
    promise.future
  }

  def updatePreview() = {
    withSessionAndReqBuilder { (session, request) =>
      request.set(requestKey(CaptureRequest.CONTROL_MODE), CameraMetadata.CONTROL_MODE_AUTO)
      request.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
      determineFlash(request)
      session.setRepeatingRequest(request.build(), null, cameraHandler)
    }
  }

  override def release(): Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      imageReader.discardFreeBuffers()
    }
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
      withSessionAndReqBuilder { (session, requestBuilder) =>
        session.stopRepeating()
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), CameraMetadata.CONTROL_AF_TRIGGER_START)
        session.capture(requestBuilder.build, null, cameraHandler)

        val focusAreas = Array(new MeteringRectangle(touchRect, MeteringRectangle.METERING_WEIGHT_MAX - 1))

        if (isMeteringAreaAESupported) {
          requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, focusAreas)
        }

        if (isMeteringAreaAFSupported) {
          requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_REGIONS), focusAreas)
        }
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_MODE), CameraMetadata.CONTROL_MODE_AUTO)
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_MODE), CameraMetadata.CONTROL_AF_MODE_AUTO)
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), CameraMetadata.CONTROL_AF_TRIGGER_START)

        val callback = new CameraCaptureSession.CaptureCallback {
          override def onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult): Unit = {
            super.onCaptureCompleted(session, request, result)
            verbose(l"capture completed, tag: ${request.getTag}")
            if (request.getTag == FOCUS_TAG) {
              requestBuilder.set(requestKey(CaptureRequest.CONTROL_AF_TRIGGER), null)
              session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
            }
          }
        }
        requestBuilder.setTag(FOCUS_TAG)
        session.capture(requestBuilder.build(), callback, cameraHandler)
      }
    } catch {
      case ex: CameraAccessException => error(l"Camera access error when creating camera session", ex); promise.failure(ex)
      case ex: IllegalStateException => error(l"The session appears to longer be active", ex); promise.failure(ex)
    }
    promise.future
  }

  def determineFlash(requestBuilder: CaptureRequest.Builder) = {
    updatedFlash match {
      case FlashMode.AUTO =>
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
        requestBuilder.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_OFF)
      case FlashMode.OFF =>
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON)
        requestBuilder.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_OFF)
      case FlashMode.ON =>
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON)
        requestBuilder.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_SINGLE)
     case FlashMode.TORCH =>
       requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON)
       requestBuilder.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_TORCH)
      case FlashMode.RED_EYE =>
        requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_MODE), CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
        requestBuilder.set(requestKey(CaptureRequest.FLASH_MODE), CameraMetadata.FLASH_MODE_OFF)
    }
    requestBuilder.set(requestKey(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER),
      CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
  }

  override def setFlashMode(fm: FlashMode): Unit = {
    updatedFlash = fm
  }

  override def getSupportedFlashModes: Set[FlashMode] =
    if (getCurrentCameraFacing == CameraMetadata.LENS_FACING_BACK && cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
      Option(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES))
        .fold(Set.empty[FlashMode])(_.map(FlashMode.get).toSet)
    } else {
      Set.empty[FlashMode]
    }

  override def setOrientation(o: Orientation): Unit = {
    orientation = o
  }

  override def getCurrentCameraFacing: Int = Option(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)).map(_.intValue()).getOrElse(0)

  private def requestKey[T](key: CaptureRequest.Key[T]): CaptureRequest.Key[Any] =
    key.asInstanceOf[CaptureRequest.Key[Any]]

  private def withSessionAndReqBuilder(f: (CameraCaptureSession, CaptureRequest.Builder) => Unit): Unit = (cameraSession, cameraRequest) match {
    case (Some(session), Some(requestBuilder)) => f(session, requestBuilder)
    case _ =>
  }

  private def isMeteringAreaAFSupported = Option(cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)).exists(_ >= 1)
  private def isMeteringAreaAESupported = Option(cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)).exists(_ >= 1)
}

object AndroidCamera2 {
  final val DEFAULT_ORIENTATION: Orientation = Orientation(0)
  final val ASPECT_TOLERANCE: Double = 0.1
  final val FOCUS_TAG: String = "FOCUS_TAG"
}

