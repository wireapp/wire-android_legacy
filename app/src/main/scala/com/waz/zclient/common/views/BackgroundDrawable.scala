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

import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.animation.{Animator, ValueAnimator}
import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.model.Dim2
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.common.views.ImageController.ImageSource
import com.waz.zclient.{Injectable, Injector}

class BackgroundDrawable(src: Signal[ImageSource],
                         context: Context,
                         screenSize: Dim2)(implicit inj: Injector, eventContext: EventContext) extends Drawable with Injectable {
  import BackgroundDrawable._

  private val images = inject[ImageController]

  private val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  private var currentBmp = Option.empty[Bitmap]
  private var prevBmp = Option.empty[Bitmap]
  private val animator = ValueAnimator.ofFloat(0, 1).setDuration(750)
  private var animationFraction = 1.0f

  private val matrix = new Matrix
  private val prevMatrix = new Matrix

  private val bmp = for {
    src <- src
    bmp <- images.imageSignal(src, BitmapRequest.Blurred(Math.min(screenSize.width, 300), BlurRadius, BlurPasses), forceDownload = true).collect { case BitmapLoaded(bm, _) => bm }
  } yield bmp

  private val colorMatrix = new ColorMatrix
  colorMatrix.setSaturation(SaturationValue)
  setColorFilter(new ColorMatrixColorFilter(colorMatrix))

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit = {
        animationFraction = animation.getAnimatedFraction
        invalidateSelf()
    }
  })

  animator.addListener(new AnimatorListener{
    override def onAnimationEnd(animation: Animator) = animationEnd()
    override def onAnimationRepeat(animation: Animator) = {}
    override def onAnimationStart(animation: Animator) = {}
    override def onAnimationCancel(animation: Animator) = animationEnd()
  })

  bmp.onUi { bmp =>
    prevBmp = currentBmp
    currentBmp = Option(bmp)
    animator.start()
  }

  private def animationEnd(): Unit = {
    prevBmp = None
    animationFraction = 0
    bitmapPaint.setAlpha(255)
    invalidateSelf()
  }

  override def draw(canvas: Canvas) = {

    currentBmp.foreach   { bm =>
      matrix.reset()
      ScaleType.CenterXCrop(matrix, bm.getWidth, bm.getHeight, screenSize)

      prevBmp.fold {
        canvas.drawBitmap(bm, matrix, bitmapPaint)
      } { prevBm =>
        val alpha = (animationFraction * 255).toInt
        prevMatrix.reset()
        ScaleType.CenterXCrop(prevMatrix, prevBm.getWidth, prevBm.getHeight, screenSize)

        bitmapPaint.setAlpha(255 - alpha)
        canvas.drawBitmap(prevBm, prevMatrix, bitmapPaint)
        bitmapPaint.setAlpha(alpha)
        canvas.drawBitmap(bm, matrix, bitmapPaint)
      }
    }
  }

  override def setColorFilter(colorFilter: ColorFilter) = bitmapPaint.setColorFilter(colorFilter)

  override def setAlpha(alpha: Int) = bitmapPaint.setAlpha(alpha)

  override def getOpacity = bitmapPaint.getAlpha
}

object BackgroundDrawable {
  val BlurRadius = 25
  val BlurPasses = 6
  val ScaleValue = 1.4f
  val SaturationValue = 2f
}
