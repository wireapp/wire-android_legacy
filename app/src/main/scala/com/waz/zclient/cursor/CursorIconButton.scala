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
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, MotionEvent, View}
import androidx.core.content.res.ResourcesCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccentColor
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.paintcode.StyleKitView.StyleKitDrawMethod
import com.waz.zclient.paintcode.{GenericStyleKitView, StyleKitView, WireStyleKit}
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.ui.views.FilledCircularBackgroundDrawable
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class CursorIconButton(context: Context, attrs: AttributeSet, defStyleAttr: Int)
  extends GenericStyleKitView(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import CursorIconButton._
  import Threading.Implicits.Ui
  import com.waz.zclient.utils.ContextUtils._

  val accentColor = inject[Signal[AccentColor]]

  val controller = inject[CursorController]
  val menuItem = Signal(Option.empty[CursorMenuItem])

  val diameter = getDimenPx(R.dimen.cursor__menu_button__diameter)

  val itemId = menuItem.collect { case Some(item) => item.resId }

  val bgColor = menuItem.flatMap {
    case Some(CursorMenuItem.Dummy) => Signal.const(Color.TRANSPARENT, Color.TRANSPARENT)
    case Some(CursorMenuItem.Send)  => accentColor.map(ac => (ac.color, ac.color))
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
    selected.onUi(setSelected)
    buttonColor.onUi(setColor)
    itemId.map[StyleKitDrawMethod]{
      case R.id.cursor_menu_item_video =>         WireStyleKit.drawVideoMessage
      case R.id.cursor_menu_item_camera =>        WireStyleKit.drawCamera
      case R.id.cursor_menu_item_draw =>          WireStyleKit.drawSketch
      case R.id.cursor_menu_item_ping =>          WireStyleKit.drawPing
      case R.id.cursor_menu_item_location =>      WireStyleKit.drawLocation
      case R.id.cursor_menu_item_emoji =>         WireStyleKit.drawEmoji
      case R.id.cursor_menu_item_keyboard =>      WireStyleKit.drawText
      case R.id.cursor_menu_item_file =>          WireStyleKit.drawAttachement
      case R.id.cursor_menu_item_gif =>           WireStyleKit.drawGIF
      case R.id.cursor_menu_item_audio_message => WireStyleKit.drawVoiceMemo
      case R.id.cursor_menu_item_more =>          WireStyleKit.drawMore
      case R.id.cursor_menu_item_less =>          WireStyleKit.drawMore
      case R.id.cursor_menu_item_send =>          WireStyleKit.drawSend
      case R.id.cursor_menu_item_mention =>       WireStyleKit.drawMentions
      case _ =>                                   StyleKitView.NoDraw
    }.onUi { f =>
      setOnDraw(f)
      invalidate()
    }

  }
}

object CursorIconButton {
  private val PRESSED_ALPHA__LIGHT = 0.32f
  private val PRESSED_ALPHA__DARK = 0.40f
  private val THRESHOLD = 0.55f
  private val DARKEN_FACTOR = 0.1f
}
