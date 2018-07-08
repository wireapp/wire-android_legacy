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
package com.waz.zclient.common.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable.OVAL
import android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP
import android.graphics.drawable.{GradientDrawable, StateListDrawable}
import android.util.AttributeSet
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.theme.ThemeUtils.isDarkTheme
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils._

object GlyphButton {
  private val PRESSED_ALPHA__LIGHT: Float = 0.32f
  private val PRESSED_ALPHA__DARK: Float = 0.40f
  private val THRESHOLD: Float = 0.55f
  private val DARKEN_FACTOR: Float = 0.1f
}

class GlyphButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends GlyphTextView(context, attrs, defStyleAttr) {
  import GlyphButton._
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private var alphaPressed: Float = 0

  def setPressedBackgroundColor(color: Int) = setBackgroundColor(Color.TRANSPARENT, color)

  def setSolidBackgroundColor(color: Int) = setBackgroundColor(color, color)

  private def setBackgroundColor(defaultColor: Int, pColor: Int) = {
    var pressedColor = pColor
    if (isDarkTheme(getContext)) alphaPressed = PRESSED_ALPHA__DARK
    else alphaPressed = PRESSED_ALPHA__LIGHT

    val avg = (Color.red(pressedColor) + Color.blue(pressedColor) + Color.green(pressedColor)) / (3 * 255.0f)
    if (avg > THRESHOLD) {
      val darken = 1.0f - DARKEN_FACTOR
      pressedColor = Color.rgb((Color.red(pressedColor) * darken).toInt, (Color.green(pressedColor) * darken).toInt, (Color.blue(pressedColor) * darken).toInt)
    }
    val pressed = ColorUtils.injectAlpha(alphaPressed, pressedColor)
    val pressedBgColor = returning(new GradientDrawable(BOTTOM_TOP, Array(pressed, pressed)))(_.setShape(OVAL))
    val defaultBgColor = returning(new GradientDrawable(BOTTOM_TOP, Array(defaultColor, defaultColor)))(_.setShape(OVAL))

    val states = new StateListDrawable
    Seq(android.R.attr.state_pressed, android.R.attr.state_focused, -android.R.attr.state_enabled).foreach(st => states.addState(Array(st), pressedBgColor))
    states.addState(new Array(0), defaultBgColor)

    setBackground(states)
    invalidate()
  }

  def initTextColor(selectedColor: Int) = {
    val (pressedColor, enabledColor, disabledColor) =
      if (!isDarkTheme(getContext)) {
        (getColor(R.color.text__primary_dark_40),
          getColor(R.color.text__primary_dark),
          getColor(R.color.text__primary_dark_16))
      } else {
        (getColor(R.color.text__primary_light__40),
          getColor(R.color.text__primary_light),
          getColor(R.color.text__primary_light_16))
      }

    val focusedColor = pressedColor

    val colors = Array(pressedColor, focusedColor, selectedColor, enabledColor, disabledColor)
    val states = Array(Array(android.R.attr.state_pressed), Array(android.R.attr.state_focused), Array(android.R.attr.state_selected), Array(android.R.attr.state_enabled), Array(-android.R.attr.state_enabled))
    val colorStateList: ColorStateList = new ColorStateList(states, colors)
    super.setTextColor(colorStateList)
  }

  def setBackgroundColors(enabledColor: Int, disabledColor: Int): Unit =
    setBackgroundColors(enabledColor, disabledColor, ColorUtils.adjustBrightness(enabledColor, 0.8f))

  def setBackgroundColors(enabledColor: Int, disabledColor: Int, pressedColor: Int): Unit =
    setBackgroundColors(enabledColor, disabledColor, pressedColor, enabledColor)

  def setBackgroundColors(enabledColor: Int, disabledColor: Int, pressedColor: Int, selectedColor: Int): Unit = {
    val drawables = Seq(pressedColor, enabledColor, disabledColor, selectedColor, enabledColor).map(circleDrawable)
    val states = Seq(android.R.attr.state_pressed, android.R.attr.state_enabled, -android.R.attr.state_enabled, android.R.attr.state_focused, 0)
    val stateDrawables = drawables.zip(states)
    val stateList = new StateListDrawable
    stateDrawables.foreach { case (d, s) => stateList.addState(Array(s), d) }
    setBackground(stateList)
    invalidate()
  }

  private def circleDrawable(color: Int) = returning(new GradientDrawable(BOTTOM_TOP, Array(color, color)))(_.setShape(OVAL))
}
