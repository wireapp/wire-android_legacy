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

import android.graphics._
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.ui.utils.TypefaceUtils

class GlyphOverlayTransformation(glyph: String) extends BitmapTransformation with DerivedLogTag {

  private val Tag: String = logTag.value + s"-$glyph"
  private val TagBytes: Array[Byte] = Tag.getBytes(Charset.forName("UTF-8"))

  private val darkenPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
    p.setColor(Color.BLACK)
    p.setAlpha(65)
  }

  private val glyphPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
    p.setTextAlign(Paint.Align.CENTER)
    p.setColor(Color.WHITE)
    p.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
  }

  override def transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap = {
    val bitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)

    val textSize = Math.min(outWidth, outHeight) / 2f
    glyphPaint.setTextSize(textSize)

    val canvas = new Canvas(bitmap)
    canvas.drawBitmap(toTransform, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG))
    canvas.drawPaint(darkenPaint)
    canvas.drawText(glyph, canvas.getClipBounds.centerX(), canvas.getClipBounds.centerY() + textSize / 2f, glyphPaint)
    canvas.setBitmap(null)

    bitmap
  }

  override def updateDiskCacheKey(messageDigest: MessageDigest): Unit = messageDigest.digest(TagBytes)

  override def hashCode(): Int = Tag.hashCode

  override def equals(obj: scala.Any): Boolean =
    obj.isInstanceOf[GlyphOverlayTransformation] &&
      obj.asInstanceOf[GlyphOverlayTransformation].Tag == this.Tag
}
