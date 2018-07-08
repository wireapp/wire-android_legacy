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
package com.waz.zclient.views

import android.graphics.{Canvas, ColorFilter, Paint, Rect}
import android.graphics.drawable.Drawable

class IconDrawable(drawIcon: (Canvas) => Unit) extends Drawable {
  private val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  override def setColorFilter(colorFilter: ColorFilter): Unit = bitmapPaint.setColorFilter(colorFilter)

  override def setAlpha(alpha: Int): Unit = bitmapPaint.setAlpha(alpha)

  override def getOpacity: Int = bitmapPaint.getAlpha

  override def draw(canvas: Canvas): Unit = drawIcon(canvas)

  override def onBoundsChange(bounds: Rect): Unit = super.onBoundsChange(bounds)
}
