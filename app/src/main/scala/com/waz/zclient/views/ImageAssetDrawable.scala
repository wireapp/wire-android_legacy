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
import com.waz.model.GenericContent.Asset
import com.waz.model._
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

  private val matrix = new Matrix()
  private val dims = Signal[Dim2]()
  private val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  private val animator = ValueAnimator.ofFloat(0, 1).setDuration(750)

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val alpha = (animation.getAnimatedFraction * 255).toInt
      bitmapPaint.setAlpha(alpha)
      invalidateSelf()
    }
  })

  val state = for {
    im <- src
    d <- dims
    state <- bitmapState(im, d.width)
  } yield state

  state.on(Threading.Ui) { _ => invalidateSelf() }

  private def bitmapState(im: ImageSource, w: Int) =
    images.imageSignal(im, request(w))
      .map[State] {
        case BitmapLoaded(bmp, true, _) => State.PreviewLoaded(im, Some(bmp))
        case BitmapLoaded(bmp, _, _) => State.Loaded(im, Some(bmp))
        case _ => State.Failed(im)
      }
      .orElse(Signal const State.Loading(im))

  // previously drawn state
  private var prev = Option.empty[State]

  override def draw(canvas: Canvas): Unit = {

    // will only use fadeIn if we previously displayed an empty bitmap
    // this way we can avoid animating if view was recycled
    def resetAnimation(state: State) = {
      animator.end()
      if (state.bmp.nonEmpty && prev.exists(_.bmp.isEmpty)) animator.start()
    }

    def updateMatrix(b: Bitmap) =
      scaleType(matrix, b.getWidth, b.getHeight, Dim2(getBounds.width(), getBounds.height()))

    def updateDrawingState(state: State) = {
      state.bmp foreach updateMatrix
      if (prev.forall(_.src != state.src)) resetAnimation(state)
    }

    state.currentValue foreach { st =>
      if (!prev.contains(st)) {
        updateDrawingState(st)
        prev = Some(st)
      }
      st.bmp foreach {
        canvas.drawBitmap(_, matrix, bitmapPaint)
      }
    }
  }

  override def onBoundsChange(bounds: Rect): Unit = dims ! Dim2(bounds.width(), bounds.height())

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    bitmapPaint.setColorFilter(colorFilter)
    invalidateSelf()
  }

  override def setAlpha(alpha: Int): Unit = {
    bitmapPaint.setAlpha(alpha)
    invalidateSelf()
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
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
    case object CenterCrop extends ScaleType {
      override def apply(matrix: Matrix, w: Int, h: Int, viewSize: Dim2): Unit = {
        val srcW = viewSize.width * h / viewSize.height
        val scale = if (srcW >= w) viewSize.width.toFloat / w else viewSize.height.toFloat / h
        val dx = - (w * scale - viewSize.width) / 2
        val dy = - (h * scale - viewSize.height) / 2

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

  sealed trait State {
    val src: ImageSource
    val bmp: Option[Bitmap] = None
  }
  object State {
    case class Loading(src: ImageSource) extends State
    case class PreviewLoaded(src: ImageSource, override val bmp: Option[Bitmap]) extends State
    case class Loaded(src: ImageSource, override val bmp: Option[Bitmap]) extends State
    case class Failed(src: ImageSource) extends State
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
