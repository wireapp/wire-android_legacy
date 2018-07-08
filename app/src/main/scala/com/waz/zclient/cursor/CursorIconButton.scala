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
import android.graphics._
import android.graphics.drawable.{Drawable, StateListDrawable}
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, MotionEvent, View}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.AccentColor
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.common.controllers.ThemeController
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

  val diameter = getDimenPx(R.dimen.cursor__menu_button__diameter)

  val glyph = menuItem.map(_.fold(R.string.empty_string)(_.glyphResId))

  val bgColor = menuItem.flatMap {
    case Some(CursorMenuItem.Dummy) => Signal.const(Color.TRANSPARENT, Color.TRANSPARENT)
    case Some(CursorMenuItem.Send)  => accentColor.map(ac => (ac.getColor, ac.getColor))
    case _ => Signal.const(Color.TRANSPARENT, getColor(R.color.light_graphite))
  }

  val buttonColor = defaultColor

  protected def defaultColor = inject[ThemeController].darkThemeSet
    .map(if (_) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector)
    .map(ResourcesCompat.getColorStateList(getResources, _, null))

  val background = defaultBackground

  protected def defaultBackground: Signal[Drawable] = bgColor.map { case (defaultColor, pressedColor) =>
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

  val selected = controller.selectedItem.zip(menuItem) map {
    case (Some(s), Some(i)) => s == i
    case _ => false
  }

  this.onClick {
    menuItem.head.foreach {
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

    background.onUi(setBackground)
    glyph.onUi(setText)
    selected.onUi(setSelected)
    buttonColor.onUi(setTextColor)

  }
}

object CursorIconButton {
  private val PRESSED_ALPHA__LIGHT = 0.32f
  private val PRESSED_ALPHA__DARK = 0.40f
  private val THRESHOLD = 0.55f
  private val DARKEN_FACTOR = 0.1f
}
