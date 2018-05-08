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
import android.content.res.ColorStateList
import android.graphics._
import android.graphics.drawable.{Drawable, StateListDrawable}
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, MotionEvent, View}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.AccentColor
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.R
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.ui.views.FilledCircularBackgroundDrawable
import com.waz.zclient.utils._


class CursorIconButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends GlyphTextView(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import CursorIconButton._
  import Threading.Implicits.Ui
  import com.waz.zclient.utils.ContextUtils._

  val accentColor = inject[Signal[AccentColor]]

  val controller = inject[CursorController]
  val menuItem = Signal(Option.empty[CursorMenuItem])

  val defaultTextColor = getCurrentTextColor

  val diameter = getResources.getDimensionPixelSize(R.dimen.cursor__menu_button__diameter)

  val glyph = for {
    item <- menuItem
    ephemeral <- controller.isEphemeralMode
  } yield
    item.fold(R.string.empty_string) { mi => if (ephemeral) mi.timedGlyphResId else mi.glyphResId }

  val bgColor = menuItem flatMap {
    case Some(CursorMenuItem.Dummy) => Signal const (Color.TRANSPARENT, Color.TRANSPARENT)
    case Some(CursorMenuItem.Send) => accentColor map { ac => (ac.getColor, ac.getColor) }
    case _ => Signal const (Color.TRANSPARENT, getColor(R.color.light_graphite))
  }

  val background = defaultBackground

  val selected = controller.selectedItem.zip(menuItem) map {
    case (Some(s), Some(i)) => s == i
    case _ => false
  }

  this.onClick {
    menuItem.head foreach {
      case Some(item) => controller.onCursorItemClick ! item
      case None => // no item, ignoring
    }
  }

  setOnLongClickListener(new View.OnLongClickListener() {
    override def onLongClick(view: View): Boolean = {
      menuItem.currentValue.flatten.foreach {
        case CursorMenuItem.AudioMessage =>
          // compatibility with old cursor handling
          controller.cursorCallback.foreach(_.onCursorButtonLongPressed(com.waz.zclient.ui.cursor.CursorMenuItem.AUDIO_MESSAGE))
        case item if item != CursorMenuItem.Dummy && item != CursorMenuItem.Send =>
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
          controller.onShowTooltip ! (item, view)
        case _ => // ignore
      }
      true
    }
  })

  // for compatibility with old cursor
  setOnTouchListener(new View.OnTouchListener() {
    override def onTouch(view: View, motionEvent: MotionEvent): Boolean = {
      menuItem.currentValue.flatten.foreach {
        case CursorMenuItem.AudioMessage =>
          controller.cursorCallback.foreach { callback =>
            callback.onMotionEventFromCursorButton(com.waz.zclient.ui.cursor.CursorMenuItem.AUDIO_MESSAGE, motionEvent)
          }
        case _ => // ignore
      }
      false
    }
  })

  override def onFinishInflate(): Unit = {
    super.onFinishInflate()
    initTextColor(accentColor.currentValue.map(_.getColor).getOrElse(R.color.text__primary_light))
    background.on(Threading.Ui) { setBackground }

    glyph.on(Threading.Ui) { setText }
    selected.on(Threading.Ui) { setSelected }
  }

  private def initTextColor(selectedColor: Int): Unit = {
    val dark = ThemeUtils.isDarkTheme(getContext)
    val enabledColor = getColor(if (dark) R.color.text__primary_dark else R.color.text__primary_light)
    val pressedColor = getColor(if (dark) R.color.text__primary_dark_40 else R.color.text__primary_light__40)
    val disabledColor = getColor(if (dark) R.color.text__primary_dark_16 else R.color.text__primary_light_16)
    val focusedColor = pressedColor
    val colors = Array(pressedColor, focusedColor, selectedColor, enabledColor, disabledColor)
    val states = Array(Array(android.R.attr.state_pressed), Array(android.R.attr.state_focused), Array(android.R.attr.state_selected), Array(android.R.attr.state_enabled), Array(-android.R.attr.state_enabled))
    setTextColor(new ColorStateList(states, colors))
  }

  protected def defaultBackground: Signal[Drawable] = bgColor map { case (defaultColor, pressedColor) =>
    val alphaPressed = if (ThemeUtils.isDarkTheme(getContext)) PRESSED_ALPHA__DARK else PRESSED_ALPHA__LIGHT
    val avg = (Color.red(pressedColor) + Color.blue(pressedColor) + Color.green(pressedColor)) / (3 * 255.0f)
    val pressed = ColorUtils.injectAlpha(alphaPressed, if (avg > THRESHOLD) {
      val darken = 1.0f - CursorIconButton.DARKEN_FACTOR
      Color.rgb((Color.red(pressedColor) * darken).toInt, (Color.green(pressedColor) * darken).toInt, (Color.blue(pressedColor) * darken).toInt)
    } else pressedColor)
    val pressedBgColor = new FilledCircularBackgroundDrawable(pressed, getDimenPx(R.dimen.cursor__menu_button__diameter))
    val states = new StateListDrawable
    states.addState(Array(android.R.attr.state_pressed), pressedBgColor)
    states.addState(Array(android.R.attr.state_focused), pressedBgColor)
    states.addState(Array(-android.R.attr.state_enabled), pressedBgColor)
    states.addState(Array[Int](), new FilledCircularBackgroundDrawable(defaultColor, getDimenPx(R.dimen.cursor__menu_button__diameter)))
    states
  }
}

object CursorIconButton {
  private val PRESSED_ALPHA__LIGHT = 0.32f
  private val PRESSED_ALPHA__DARK = 0.40f
  private val THRESHOLD = 0.55f
  private val DARKEN_FACTOR = 0.1f
}
