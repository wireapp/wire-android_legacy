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
package com.waz.zclient.cursor

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.{AttributeSet, TypedValue}
import android.view.Gravity
import com.waz.model.AccentColor
import com.waz.model.{ConvExpiry, EphemeralDuration, MessageExpiry}
import com.wire.signals.Signal
import com.waz.zclient.paintcode.{EphemeralIcon, HourGlassIcon}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getColor, getDimenPx}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

class EphemeralTimerButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends TypefaceTextView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__small))

  val accentColor         = inject[Signal[AccentColor]]

  val hasEnforcedTimeout  = Signal[Boolean](false)
  val ephemeralExpiration = Signal[Option[EphemeralDuration]](None)
  val darkTheme           = Signal(true) //sharing controller is always in "dark" theme

  val lengthAndUnit = ephemeralExpiration.map(_.map(_.display))

  val (len, unit) =
    (lengthAndUnit.map(_.map(_._1)), lengthAndUnit.map(_.map(_._2)))

  val display = len.map(_.map(_.toString).getOrElse(""))

  val color =
    ephemeralExpiration.flatMap {
      case Some(MessageExpiry(_)) => accentColor.map(_.color)
      case Some(ConvExpiry(_))    => Signal.const(getColor(R.color.light_graphite))
      case _ => darkTheme.map {
        case true  => getColor(R.color.text__primary_dark)
        case false => getColor(R.color.text__primary_light)
      }
    }

  val iconSize = ephemeralExpiration.map {
    case Some(_) => R.dimen.wire__padding__24
    case _       => R.dimen.wire__padding__16
  }.map(getDimenPx)

  //For QA testing
  val contentDescription = lengthAndUnit.map {
    case Some((l, unit)) => s"$l$unit"
    case None => "off"
  }

  val drawable: Signal[Drawable] =
    for {
      color <- color
      unit  <- unit
    } yield {
      unit match {
        case Some(u) => EphemeralIcon(color, u)
        case _       => HourGlassIcon(color)
      }
    }

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()

    setGravity(Gravity.CENTER)

    display.onUi(setText)
    hasEnforcedTimeout.onUi{ isEnforced =>
      setEnabled(!isEnforced)
    }
    drawable.onUi(setBackgroundDrawable)
    contentDescription.onUi(setContentDescription)

    Signal.zip(color, iconSize).onUi {
      case (c, s) =>
        setTextColor(c)
        this.setWidthAndHeight(Some(s), Some(s))
    }
  }
}
