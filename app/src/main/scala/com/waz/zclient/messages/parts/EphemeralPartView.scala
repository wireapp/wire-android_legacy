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
package com.waz.zclient.messages.parts

import android.content.res.ColorStateList
import android.graphics.drawable.{ColorDrawable, Drawable}
import android.graphics.{Canvas, Paint, RectF}
import android.view.View
import android.widget.{ImageView, TextView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccentColor
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageViewPart
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.{ColorUtils, TypefaceUtils}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}
import com.wire.signals.ext.ClockSignal

trait EphemeralPartView extends MessageViewPart { self: ViewHelper =>

  lazy val redactedTypeface = TypefaceUtils.getTypeface(TypefaceUtils.getRedactedTypedaceName)
  lazy val accentController = inject[AccentColorController]

  val expired = message map { m => m.isEphemeral && m.expired }

  val ephemeralColorDrawable: Signal[Option[Drawable]] =
    for {
      hide <- expired
      acc <- accentController.accentColor
    } yield
      if (hide) Some(new ColorDrawable(ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(getContext), acc.color)))
      else None

  def registerEphemeral(textView: TextView) = {
    val originalTypeface = textView.getTypeface
    val originalColor = textView.getTextColors

    val typeface = expired map { if (_) redactedTypeface else originalTypeface }
    val color = expired flatMap[Either[ColorStateList, AccentColor]] {
      case true => accentController.accentColor.map { Right(_) }
      case false => Signal const Left(originalColor)
    }

    typeface { textView.setTypeface }
    color {
      case Left(csl) => textView.setTextColor(csl)
      case Right(ac) => textView.setTextColor(ac.color)
    }
  }

  def ephemeralDrawable(drawable: Drawable) =
    for {
      hide <- expired
      acc <- accentController.accentColor
    } yield
      if (hide) new ColorDrawable(ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(getContext), acc.color))
      else drawable

  def registerEphemeral(view: View, background: Drawable): Unit =
    ephemeralDrawable(background).on(Threading.Ui) { view.setBackground }

  def registerEphemeral(imageView: ImageView, imageDrawable: Drawable): Unit =
    ephemeralDrawable(imageDrawable).on(Threading.Ui) { imageView.setImageDrawable }
}

trait EphemeralIndicatorPartView
  extends MessageViewPart
    with ViewHelper
    with DerivedLogTag {

  private val paint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(getColor(R.color.white_80)))
  private val bgPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(getColor(R.color.light_graphite)))
  private val circleSize = getDimenPx(R.dimen.ephemeral__animating_dots__width)
  private val paddingStart = (getDimenPx(R.dimen.content__padding_left) - circleSize) / 2
  private val paddingTop = getDimenPx(R.dimen.wire__padding__6)
  private val borderPadding = getDimenPx(R.dimen.wire__padding__1)

  private val timerAngle = messageAndLikes
    .map { m => (m.message.ephemeral, m.message.expired, m.message.expiryTime) }  // optimisation to ignore unrelated changes
    .flatMap {
    case (ephemeral, expired, expiryTime) =>
      if (expired) Signal const 360
      else expiryTime.fold(Signal const 0) { time =>
        val interval = ephemeral.get / 360
        ClockSignal(interval).map { now =>
          val remaining = time.toEpochMilli - now.toEpochMilli
          360 - ((remaining * 360f / ephemeral.get.toMillis).toInt max 0 min 360)
        }
      }
  }

  private val state = for {
    ephemeral <- messageAndLikes.map(_.message.isEphemeral)
    angle <- timerAngle
  } yield (ephemeral, angle)

  state.on(Threading.Ui) { _ => invalidate() }
  setWillNotDraw(false)

  override def onDraw(canvas: Canvas): Unit = state.currentValue match {
    case Some((true, angle)) if canvas != null && canvas.getHeight > 0 =>
      val top = Math.min(paddingTop, (canvas.getHeight - circleSize) / 2)
      val circleRect = new RectF(paddingStart, top, paddingStart + circleSize, top + circleSize)
      val innerRect = returning(new RectF(circleRect))(_.inset(borderPadding, borderPadding))

      canvas.drawArc(circleRect, 0, 360, true, bgPaint)
      canvas.drawArc(innerRect, -90, angle, true, paint)
    case _ => // nothing to draw, not ephemeral or not loaded
  }
}
