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
package com.waz.zclient.views

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics._
import android.graphics.drawable.Drawable
import android.net.Uri
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.GenericContent.Asset
import com.waz.model.{AnyAssetData, AssetId, AssetPreviewData, ImageAssetData}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapRequest.Regular
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.assets.AssetService.{BitmapRequest, BitmapResult}
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType, State}
import com.waz.zclient.views.ImageController.{ImageSource, ImageUri, ProtoImage, WireImage}
import com.waz.zclient.{Injectable, Injector}

//TODO could merge with logic from the ChatheadView to make a very general drawable for our app
class ImageAssetDrawable(
                          src: Signal[ImageSource],
                          scaleType: ScaleType = ScaleType.FitXY,
                          request: RequestBuilder = RequestBuilder.Regular
                        )(implicit inj: Injector, eventContext: EventContext) extends Drawable with Injectable {

  val images = inject[ImageController]

  val state = Signal[State](State.Loading)

  private var currentBitmap = Option.empty[Bitmap]
  private val matrix = new Matrix()

  private val width = Signal[Int]()

  private val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  bitmapPaint.setColor(Color.TRANSPARENT)
  private val animator = ValueAnimator.ofFloat(0, 1).setDuration(750)

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val alpha = (animation.getAnimatedFraction * 255).toInt
      bitmapPaint.setAlpha(alpha)
      invalidateSelf()
    }
  })

  private val bitmap = for {
    w <- width
    s <- src
    res <- images.imageSignal(s, request(w))
  } yield res

  private var currentSrc: ImageSource = _

  src { img =>
    // image source signal should be processed on UI thread, this is needed to avoid flickering
    // we can not avoid that requirement by using `on(Threading.Ui)`, it wold flicker, as changing src would run on next frame
    Threading.assertUiThread()

    if (img != currentSrc) {
      currentSrc = img
      currentBitmap = None
      state ! State.Loading
      invalidateSelf()
    }
  }

  bitmap.on(Threading.Ui) {
    case BitmapLoaded(bmp, preview, _) =>
      if (!currentBitmap.contains(bmp)) {
        verbose(s"bitmap changed, width: ${width.currentValue}")
        currentBitmap = Some(bmp)
        scaleType(matrix, bmp.getWidth, bmp.getHeight, getBounds)
        invalidateSelf()
        if (state.currentValue.contains(State.Loading)) animator.start()
      }
      state ! (if (preview) State.PreviewLoaded else State.Loaded)
    case res =>
      currentBitmap = None
      state ! State.Failed
      invalidateSelf()
  }

  override def setBounds(left: Int, top: Int, right: Int, bottom: Int): Unit = {
    verbose(s"setBounds: left: $left, top: $top, right: $right, bottom: $bottom")
    super.setBounds(left, top, right, bottom)
    currentBitmap foreach { b =>
      scaleType(matrix, b.getWidth, b.getHeight, getBounds)
    }
    width ! (right - left)
  }

  override def draw(canvas: Canvas): Unit =
    currentBitmap foreach { b =>
      canvas.drawBitmap(b, matrix, bitmapPaint)
    }

  override def setColorFilter(colorFilter: ColorFilter): Unit = bitmapPaint.setColorFilter(colorFilter)

  override def setAlpha(alpha: Int): Unit = bitmapPaint.setAlpha(alpha)

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
}

object ImageAssetDrawable {

  sealed trait ScaleType {
    def apply(matrix: Matrix, w: Int, h: Int, bounds: Rect): Unit
  }
  object ScaleType {
    case object FitXY extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, bounds: Rect): Unit =
        matrix.setScale(bounds.width().toFloat / w, bounds.height().toFloat / h)
    }
    case object CenterCrop extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, bounds: Rect): Unit = {
        val srcW = bounds.width() * h / bounds.height()
        val scale = if (srcW >= w) bounds.width().toFloat / w else bounds.height().toFloat / h
        val dx = - (w * scale - bounds.width()) / 2
        val dy = - (h * scale - bounds.height()) / 2

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
      }
    }
  }

  type RequestBuilder = Int => BitmapRequest
  object RequestBuilder {
    val Regular: RequestBuilder = BitmapRequest.Regular(_)
    val Single: RequestBuilder = BitmapRequest.Single(_)
    val Static: RequestBuilder = BitmapRequest.Static(_)
  }

  sealed trait State
  object State {
    case object Loading extends State
    case object PreviewLoaded extends State
    case object Loaded extends State
    case object Failed extends State
  }
}

class ImageController(implicit inj: Injector) extends Injectable {

  val zMessaging = inject[Signal[ZMessaging]]

  def imageData(id: AssetId) = zMessaging.flatMap { zms =>
    zms.assetsStorage.signal(id) map {
      case im: ImageAssetData => im
      case AnyAssetData(_, conv, _, _, _, _, Some(AssetPreviewData.Image(preview)), _, _, _, _) => ImageAssetData(id, conv, Seq(preview))
      case _ => ImageAssetData.Empty
    }
  }

  def imageSignal(id: AssetId, width: Int): Signal[BitmapResult] =
    imageSignal(id, Regular(width))

  def imageSignal(id: AssetId, req: BitmapRequest): Signal[BitmapResult] =
    for {
      zms <- zMessaging
      data <- imageData(id)
      res <- BitmapSignal(data, req, zms.imageLoader, zms.imageCache)
    } yield res

  def imageSignal(uri: Uri, req: BitmapRequest): Signal[BitmapResult] =
    BitmapSignal(ImageAssetData(uri), req, ZMessaging.currentGlobal.imageLoader, ZMessaging.currentGlobal.imageCache)

  def imageSignal(asset: Asset, req: BitmapRequest): Signal[BitmapResult] =
    zMessaging flatMap { zms => BitmapSignal(asset, req, zms.imageLoader, zms.imageCache) }

  def imageSignal(src: ImageSource, req: BitmapRequest): Signal[BitmapResult] = src match {
    case WireImage(id) => imageSignal(id, req)
    case ProtoImage(asset) => imageSignal(asset, req)
    case ImageUri(uri) => imageSignal(uri, req)
  }
}

object ImageController {

  sealed trait ImageSource
  case class WireImage(id: AssetId) extends ImageSource
  case class ProtoImage(asset: Asset) extends ImageSource
  case class ImageUri(uri: Uri) extends ImageSource
}
