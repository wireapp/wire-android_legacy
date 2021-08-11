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
package com.waz.zclient.messages.parts.assets

import android.graphics._
import android.graphics.drawable.Drawable
import com.waz.model.AccentColor
import com.waz.threading.Threading
import com.wire.signals.{EventContext, Signal}
import com.waz.zclient.common.views.ProgressDotsDrawable
import com.waz.zclient.messages.parts.assets.AssetPart.AssetPartViewState
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Offset
import com.waz.zclient.{R, WireContext}

class AssetBackground(viewState: Signal[AssetPartViewState], accent: Signal[AccentColor])(implicit context: WireContext, eventContext: EventContext) extends Drawable with Drawable.Callback {
  private val cornerRadius = toPx(4)
  private val defColor = getColor(R.color.light_graphite_8)

  private val backgroundPaint = new Paint
  backgroundPaint.setColor(defColor)

  private val dots = new ProgressDotsDrawable
  dots.setCallback(this)

  val padding = Signal(Offset.Empty) //empty signifies match_parent

  private var _showDots = false
  private var _padding = Offset.Empty

  (for {
    state <- viewState
    acc <- accent
    pad <- padding
  } yield (state, acc, pad)).on(Threading.Ui) {
    case (state, acc, pad) =>
      _showDots = state == AssetPartViewState.Loading
      _padding = pad

      if (state == AssetPartViewState.Obfuscated) {
        val backgroundColor = ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(context), acc.color)
        backgroundPaint.setColor(backgroundColor)
      } else {
        backgroundPaint.setColor(defColor)
      }

      invalidateSelf()
  }

  override def draw(canvas: Canvas): Unit = {

    val bounds =
      if (_padding == Offset.Empty) getBounds
      else {
        val b = getBounds
        new Rect(b.left + _padding.l, b.top + _padding.t, b.right - _padding.r, b.bottom - _padding.b)
      }

    canvas.drawRoundRect(new RectF(bounds), cornerRadius, cornerRadius, backgroundPaint)
    if (_showDots) dots.draw(canvas)
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    backgroundPaint.setColorFilter(colorFilter)
    dots.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int): Unit = {
    backgroundPaint.setAlpha(alpha)
    dots.setAlpha(alpha)
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT

  override def scheduleDrawable(who: Drawable, what: Runnable, when: Long): Unit = scheduleSelf(what, when)

  override def invalidateDrawable(who: Drawable): Unit = invalidateSelf()

  override def unscheduleDrawable(who: Drawable, what: Runnable): Unit = unscheduleSelf(what)
}
