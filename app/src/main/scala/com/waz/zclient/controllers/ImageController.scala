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
package com.waz.zclient.controllers

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.{AnyAssetData, AssetId, AssetPreviewData, ImageAssetData}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapRequest.Regular
import com.waz.service.assets.AssetService.{BitmapRequest, BitmapResult}
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}

//TODO could merge with logic from the ChatheadView to make a very general drawable for our app
class ImageAssetDrawable(implicit inj: Injector, eventContext: EventContext) extends Drawable with Injectable {
  self =>

  val images = inject[ImageController]

  private val assetId = Signal[AssetId]
  private var currentBitmap = Option.empty[Bitmap]

  val width = Signal[Int]()

  private val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  bitmapPaint.setColor(Color.TRANSPARENT)
  private val animator = ValueAnimator.ofFloat(0, 1).setDuration(750)

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
      val alpha = (animation.getAnimatedFraction * 255).toInt
      verbose(s"setting alpha: $alpha")
      bitmapPaint.setAlpha(alpha)
      invalidateSelf()
    }
  })

  val bitmap = for {
    w <- width
    id <- assetId
    res <- images.imageSignal(id, w)
  } yield res match {
    case BitmapResult.BitmapLoaded(bmp, _, _) => bmp
    case _ => null
  }

  def setAssetId(id: AssetId): Unit = {
    if (!assetId.currentValue.contains(id)) {
      currentBitmap = None //
      assetId ! id
    }
  }

  bitmap.on(Threading.Ui) { b =>
    if (!currentBitmap.contains(b)) {
      currentBitmap = Some(b)
      animator.start()
    }
  }

  override def setBounds(left: Int, top: Int, right: Int, bottom: Int): Unit = {
    verbose(s"setBounds: left: $left, top: $top, right: $right, bottom: $bottom")
    super.setBounds(left, top, right, bottom)
    width ! (right - left)
  }

  override def draw(canvas: Canvas): Unit = {
    currentBitmap.fold {
      canvas.drawColor(Color.TRANSPARENT)
    } { b =>
      canvas.drawBitmap(b, null, getBounds, bitmapPaint)
    }
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = ()

  override def setAlpha(alpha: Int): Unit = ()

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
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
}
