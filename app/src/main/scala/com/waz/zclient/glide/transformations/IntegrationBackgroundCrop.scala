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
package com.waz.zclient.glide.transformations

import java.nio.charset.Charset
import java.security.MessageDigest

import android.graphics.Paint.Join
import android.graphics._
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning

class IntegrationBackgroundCrop extends BitmapTransformation with DerivedLogTag {

  private val Tag: String = logTag.value
  private val TagBytes: Array[Byte] = Tag.getBytes(Charset.forName("UTF-8"))

  private val circlePaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(Color.WHITE))
  private val borderPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
    p.setColor(Color.BLACK)
    p.setStyle(Paint.Style.STROKE)
    p.setStrokeJoin(Join.ROUND)
    p.setAlpha(20)
  }
  private val bitmapPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG)) {
    _.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP))
  }

  override def transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap = {
    val bitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)

    val radius = Math.min(outWidth, outHeight) * 0.2f
    val dx = (outWidth - toTransform.getWidth) / 2f
    val dy = (outHeight - toTransform.getHeight) / 2f

    val strokeWidth = Math.min(outWidth, outHeight) * 0.01f
    borderPaint.setStrokeWidth(strokeWidth)

    val canvas = new Canvas(bitmap)
    val targetRect = new RectF(canvas.getClipBounds)
    targetRect.inset(strokeWidth, strokeWidth)
    canvas.drawRoundRect(targetRect, radius, radius, circlePaint)
    canvas.drawBitmap(toTransform, dx, dy, bitmapPaint)
    canvas.drawRoundRect(targetRect, radius, radius, borderPaint)
    canvas.setBitmap(null)

    bitmap
  }

  override def updateDiskCacheKey(messageDigest: MessageDigest): Unit = messageDigest.digest(TagBytes)

  override def hashCode(): Int = Tag.hashCode

  override def equals(obj: scala.Any): Boolean = obj.isInstanceOf[IntegrationBackgroundCrop]
}
