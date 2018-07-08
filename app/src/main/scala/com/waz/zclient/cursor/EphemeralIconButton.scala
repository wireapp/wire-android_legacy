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
import com.waz.api.AccentColor
import com.waz.model.{ConvExpiry, MessageExpiry}
import com.waz.utils.events.Signal
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.paintcode._
import com.waz.zclient.ui.text.ThemedTextView
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class EphemeralIconButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends ThemedTextView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  val accentColor = inject[Signal[AccentColor]]
  val controller = inject[CursorController]

  setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__small))

  val color =
    controller.ephemeralExp.flatMap {
      case Some(MessageExpiry(_)) => accentColor.map(_.getColor)
      case Some(ConvExpiry(_)) => Signal.const(getColor(R.color.light_graphite))
      case _ => Signal.const(getStyledColor(R.attr.wirePrimaryTextColor))
    }

  //TODO a bit hacky - maybe the icons should take a size and draw themselves centred on the canvas
  val iconSize = controller.ephemeralExp.map {
    case Some(_) => R.dimen.wire__padding__24
    case _       => R.dimen.wire__padding__16
    }.map(getDimenPx)

  val unitAndValue = controller.conv.map(_.ephemeralExpiration.map(_.display))

  val display = unitAndValue.map(_.map(_._1.toString).getOrElse(""))

  //For QA testing
  val contentDescription = unitAndValue.map {
    case Some((l, unit)) => s"$l$unit"
    case None => "off"
  }

  val drawable: Signal[Drawable] =
    for {
      color <- color
      unit  <- controller.ephemeralExp.map(_.map(_.display._2))
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

    drawable.onUi(setBackgroundDrawable)
    contentDescription.onUi(setContentDescription)
    
    color.onUi(setTextColor)
    iconSize.onUi { size =>
      this.setWidthAndHeight(Some(size), Some(size))
    }
    controller.ephemeralBtnVisible.onUi(this.setVisible)
  }

  setOnClickListener(new OnDoubleClickListener() {
    override def onDoubleClick(): Unit =
      controller.toggleEphemeralMode()

    override def onSingleClick(): Unit = {
      controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EPHEMERAL)
    }
  })
}
