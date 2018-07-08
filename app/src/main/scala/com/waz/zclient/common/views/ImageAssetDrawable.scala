/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.common.views

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.ZLog.ImplicitTag._
import com.waz.content.UserPreferences
import com.waz.model.AssetData.{IsImage, IsVideo}
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.{BitmapLoaded, LoadingFailed}
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.URI
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType, State}
import com.waz.zclient.common.views.ImageController._
import com.waz.zclient.utils.Offset
import com.waz.zclient.{Injectable, Injector}

//TODO could merge with logic from the ChatheadView to make a very general drawable for our app
class ImageAssetDrawable(src: Signal[ImageSource],
                         scaleType: ScaleType = ScaleType.FitXY,
                         request: RequestBuilder = RequestBuilder.Regular,
                         background: Option[Drawable] = None,
                         animate: Boolean = true,
                         forceDownload: Boolean = true)
                        (implicit inj: Injector, eventContext: EventContext) extends Drawable with Injectable {

  val images = inject[ImageController]

  private val matrix = new Matrix()
  private val dims = Signal[Dim2]()
  protected val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  private val animator = ValueAnimator.ofFloat(0, 1).setDuration(750)

  val padding = Signal(Offset.Empty)

  val fixedBounds = Signal(Option.empty[Rect])

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      if (animate) {
        val alpha = (animation.getAnimatedFraction * 255).toInt
        bitmapPaint.setAlpha(alpha)
        background.foreach(_.setAlpha(255 - alpha))
        invalidateSelf()
      }
    }
  })

  val state = for {
    im    <- src
    d     <- dims if d.width > 0
    _     <- fixedBounds
    p     <- padding
    state <- bitmapState(im, d.width - p.l - p.r)
  } yield state

  private var _state = Option.empty[State]

  state.on(Threading.Ui) { st =>
    _state = Some(st)
    invalidateSelf()
  }

  background foreach { bg =>
    dims.zip(padding) { case (_, Offset(l, t, r, b)) =>
      val bounds = getBounds
      bg.setBounds(bounds.left + l, bounds.top + t, bounds.right - r, bounds.bottom - b)
    }
  }

  private def bitmapState(im: ImageSource, w: Int) =
    images.imageSignal(im, request(w), forceDownload)
      .map[State] {
        case BitmapLoaded(bmp, etag) => State.Loaded(im, Some(bmp), etag)
        case LoadingFailed(ex) => State.Failed(im, Some(ex))
        case _ => State.Failed(im)
      }
      .orElse(Signal const State.Loading(im))

  // previously drawn state
  private var prev = Option.empty[State]

  override def draw(canvas: Canvas): Unit = {

    // will only use fadeIn if we previously displayed an empty bitmap
    // this way we can avoid animating if view was recycled
    def resetAnimation(state: State) = {
      animator.cancel()
      if (state.bmp.nonEmpty && prev.exists(_.bmp.isEmpty)) {
        animator.start()
      }
    }

    def updateMatrix(b: Bitmap) = {
      val bounds = fixedBounds.currentValue.flatten.fold(getBounds)(b => b)
      val p = padding.currentValue.getOrElse(Offset.Empty)
      scaleType(matrix, b.getWidth, b.getHeight, Dim2(bounds.width() - p.l - p.r, bounds.height() - p.t - p.b))
      matrix.postTranslate(bounds.left + p.l, bounds.top + p.t)
    }

    def updateDrawingState(state: State) = {
      state.bmp foreach updateMatrix
      if (prev.forall(p => p.src != state.src || p.bmp.isEmpty != state.bmp.isEmpty)) resetAnimation(state)
    }

    _state foreach { st =>
      if (!prev.contains(st)) {
        updateDrawingState(st)
        prev = Some(st)
      }

      if (st.bmp.isEmpty || bitmapPaint.getAlpha < 255)
        background foreach { _.draw(canvas) }

      st.bmp foreach { bm =>
        drawBitmap(canvas, bm, matrix, bitmapPaint)
      }

    }
  }

  protected def drawBitmap(canvas: Canvas, bm: Bitmap, matrix: Matrix, bitmapPaint: Paint): Unit =
    canvas.drawBitmap(bm, matrix, bitmapPaint)

  override def onBoundsChange(bounds: Rect): Unit = {
    dims ! Dim2(bounds.width(), bounds.height())
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    bitmapPaint.setColorFilter(colorFilter)
    invalidateSelf()
  }

  override def setAlpha(alpha: Int): Unit = {
    bitmapPaint.setAlpha(alpha)
    invalidateSelf()
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT

  override def getIntrinsicHeight: Int = dims.currentValue.map(_.height).getOrElse(-1)

  override def getIntrinsicWidth: Int = dims.currentValue.map(_.width).getOrElse(-1)
}

object ImageAssetDrawable {

  sealed trait ScaleType {
    def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit
  }
  object ScaleType {
    case object FitXY extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit =
        matrix.setScale(viewSize.width.toFloat / w, viewSize.height.toFloat / h)
    }
    case object FitY extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit = {
        val scale = viewSize.height.toFloat / h
        matrix.setScale(scale, scale)
        matrix.postTranslate(- (w * scale - viewSize.width) / 2, 0)
      }
    }
    case object CenterCrop extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit = {
        val scale = math.max(viewSize.width.toFloat / w, viewSize.height.toFloat / h)
        val dx = - (w * scale - viewSize.width) / 2
        val dy = - (h * scale - viewSize.height) / 2

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
      }
    }
    case object CenterInside extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit = {
        val scale = math.min(viewSize.width.toFloat / w, viewSize.height.toFloat / h)
        val dx = - (w * scale - viewSize.width) / 2
        val dy = - (h * scale - viewSize.height) / 2

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
      }
    }
    case object CenterXCrop extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit = {
        val scale = math.max(viewSize.width.toFloat / w, viewSize.height.toFloat / h)
        val dx = - (w * scale - viewSize.width) / 2

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, 0)
      }
    }
  }

  type RequestBuilder = Int => BitmapRequest

  object RequestBuilder {
    val Regular: RequestBuilder = BitmapRequest.Regular(_)
    val RegularMirrored: RequestBuilder = BitmapRequest.Regular(_, mirror = true)
    val Single: RequestBuilder = BitmapRequest.Single(_)
    val Round: RequestBuilder = BitmapRequest.Round(_)
    val Blurred: RequestBuilder = BitmapRequest.Blurred(_)
  }

  sealed trait State {
    val src: ImageSource
    val bmp: Option[Bitmap] = None
  }

  object State {
    case class Loading(src: ImageSource) extends State
    case class Loaded(src: ImageSource, override val bmp: Option[Bitmap], etag: Int = 0) extends State
    case class Failed(src: ImageSource, ex: Option[Throwable] = None) extends State
  }

  def apply(uri: URI, scaleType: ScaleType = ScaleType.CenterCrop)(implicit inj: Injector, eventContext: EventContext): ImageAssetDrawable = {
    val asset = AssetData.newImageAssetFromUri(uri = uri)
    new ImageAssetDrawable(Signal.const(DataImage(asset)), scaleType)
  }

  def apply(imageData: Array[Byte], isMirrored: Boolean)(implicit inj: Injector, eventContext: EventContext): ImageAssetDrawable = {
    val asset = AssetData.newImageAsset(tag = Medium).copy(sizeInBytes = imageData.length, data = Some(imageData))
    new ImageAssetDrawable(
      Signal.const(DataImage(asset)),
      scaleType = ScaleType.CenterCrop,
      request = if (isMirrored) RequestBuilder.RegularMirrored else RequestBuilder.Regular
    )
  }

  def apply(imageData: Array[Byte])(implicit inj: Injector, eventContext: EventContext): ImageAssetDrawable = apply(imageData, isMirrored = false)
}

class RoundedImageAssetDrawable (
                                  src: Signal[ImageSource],
                                  scaleType: ScaleType = ScaleType.FitXY,
                                  request: RequestBuilder = RequestBuilder.Regular,
                                  background: Option[Drawable] = None,
                                  animate: Boolean = true,
                                  cornerRadius: Float = 0
                                )(implicit inj: Injector, eventContext: EventContext) extends ImageAssetDrawable(src, scaleType, request, background, animate) {

  override protected def drawBitmap(canvas: Canvas, bm: Bitmap, matrix: Matrix, bitmapPaint: Paint): Unit = {
    val tempBm = Bitmap.createBitmap(getBounds.width, getBounds.height, Bitmap.Config.ARGB_8888)
    val tempCanvas = new Canvas(tempBm)

    tempCanvas.drawBitmap(bm, matrix, null)

    val shader = new BitmapShader(tempBm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val boundsRect = new RectF(0.0f, 0.0f, getBounds.width, getBounds.height)

    bitmapPaint.setShader(shader)
    canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, bitmapPaint)
  }
}

class IntegrationAssetDrawable (
                                  src: Signal[ImageSource],
                                  scaleType: ScaleType = ScaleType.FitXY,
                                  request: RequestBuilder = RequestBuilder.Regular,
                                  background: Option[Drawable] = None,
                                  animate: Boolean = true
                                )(implicit inj: Injector, eventContext: EventContext) extends ImageAssetDrawable(src, scaleType, request, background, animate) {

  private val StrokeWidth = 2f
  private val StrokeAlpha = 20

  val drawHelper = IntegrationSquareDrawHelper()

  override protected def drawBitmap(canvas: Canvas, bm: Bitmap, matrix: Matrix, bitmapPaint: Paint): Unit =
    drawHelper.draw(canvas, bm, getBounds, matrix, bitmapPaint)
}

case class IntegrationSquareDrawHelper() {

  private val StrokeWidth = 2f
  private val StrokeAlpha = 20

  private lazy val whitePaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ _.setColor(Color.WHITE) }
  private lazy val borderPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { paint =>
    paint.setStyle(Paint.Style.STROKE)
    paint.setColor(Color.BLACK)
    paint.setAlpha(StrokeAlpha)
    paint.setStrokeWidth(StrokeWidth)
  }

  def cornerRadius(size: Float) = size * 0.2f

  def draw(canvas: Canvas, bm: Bitmap, bounds: Rect, matrix: Matrix, bitmapPaint: Paint): Unit = {
    val tempBm = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
    val tempCanvas = new Canvas(tempBm)

    tempCanvas.drawBitmap(bm, matrix, null)

    val shader = new BitmapShader(tempBm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val outerRect = new RectF(StrokeWidth, StrokeWidth, bounds.width - StrokeWidth, bounds.height - StrokeWidth)
    val innerRect = new RectF(StrokeWidth * 2, StrokeWidth * 2, bounds.width - StrokeWidth * 2, bounds.height - StrokeWidth * 2)

    val radius = cornerRadius(bounds.width)

    bitmapPaint.setShader(shader)
    canvas.drawRoundRect(innerRect, radius, radius, whitePaint)
    canvas.drawRoundRect(innerRect, radius, radius, bitmapPaint)
    canvas.drawRoundRect(outerRect, radius, radius, borderPaint)
  }
}

class ImageController(implicit inj: Injector) extends Injectable {

  val zMessaging = inject[Signal[ZMessaging]]

  def imageData(id: AssetId) = zMessaging.flatMap {
    zms => zms.assetsStorage.signal(id).flatMap {
      case a@IsImage() => Signal.const(a)
      case a@IsVideo() => a.previewId.fold(Signal.const(AssetData.Empty))(zms.assetsStorage.signal)
      case _ => Signal.const(AssetData.Empty)
    }
  }

  def imageSignal(id: AssetId, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] =
    zMessaging.flatMap(imageSignal(_, id, req, forceDownload))

  def imageSignal(zms: ZMessaging, id: AssetId, req: BitmapRequest, forceDownload: Boolean = true): Signal[BitmapResult] =
    for {
      data <- imageData(id)
      res <- BitmapSignal(data, req, zms.imageLoader, zms.network, zms.assetsStorage.get, zms.userPrefs.preference(UserPreferences.DownloadImagesAlways).signal, forceDownload)
    } yield res

  def imageSignal(uri: URI, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] =
    BitmapSignal(AssetData(source = Some(uri)), req, ZMessaging.currentGlobal.imageLoader, ZMessaging.currentGlobal.network, forceDownload = forceDownload)

  def imageSignal(data: AssetData, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] =
    zMessaging flatMap { zms => BitmapSignal(data, req, zms.imageLoader, zms.network, zms.assetsStorage.get, forceDownload = forceDownload) }

  def imageSignal(src: ImageSource, req: BitmapRequest, forceDownload: Boolean): Signal[BitmapResult] = src match {
    case WireImage(id) => imageSignal(id, req, forceDownload)
    case ImageUri(uri) => imageSignal(uri, req, forceDownload)
    case DataImage(data) => imageSignal(data, req, forceDownload)
    case NoImage() => Signal.empty[BitmapResult]
  }
}

object ImageController {

  sealed trait ImageSource
  case class WireImage(id: AssetId) extends ImageSource
  case class DataImage(data: AssetData) extends ImageSource
  case class ImageUri(uri: URI) extends ImageSource
  case class NoImage() extends ImageSource
}
