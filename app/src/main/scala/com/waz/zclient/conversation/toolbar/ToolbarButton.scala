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
package com.waz.zclient.conversation.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.{GradientDrawable, StateListDrawable}
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.{FrameLayout, TableLayout}
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.ContextUtils._

abstract class ToolbarButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val PRESSED_ALPHA__LIGHT: Float = 0.32f
  private val PRESSED_ALPHA__DARK: Float = 0.40f
  private val TRESHOLD: Float = 0.55f
  private val DARKEN_FACTOR: Float = 0.1f
  private var alphaPressed: Float = 0.0f
  private var toolbarItem: ToolbarItem = null
  private val params = new TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
  private lazy val glyphTextView = ViewUtils.getView[GlyphTextView](this, R.id.icon)

  setLayoutParams(params)
  setLongClickable(true)

  def setToolbarItem(toolbarItem: ToolbarItem): Unit = {
    this.toolbarItem = toolbarItem
    setTag(toolbarItem)
    glyphTextView.setText(toolbarItem.glyphResId)
  }

  def getToolbarItem: ToolbarItem = toolbarItem

  def showEphemeralMode(color: Int): Unit =  {
    glyphTextView.setTextColor(color)
    if (toolbarItem != null) glyphTextView.setText(toolbarItem.timedGlyphResId)
  }

  def hideEphemeralMode(color: Int): Unit = {
    glyphTextView.setTextColor(color)
    if (toolbarItem != null) glyphTextView.setText(toolbarItem.glyphResId)
  }

  def setPressedBackgroundColor(color: Int): Unit = {
    setBackgroundColor(Color.TRANSPARENT, color)
  }

  def setSolidBackgroundColor(color: Int): Unit = {
    setBackgroundColor(color, color)
  }

  private def setBackgroundColor(defaultColor: Int, pressedColor: Int): Unit = {
    if (ThemeUtils.isDarkTheme(getContext)) alphaPressed = PRESSED_ALPHA__DARK
    else alphaPressed = PRESSED_ALPHA__LIGHT
    val avg: Float = (Color.red(pressedColor) + Color.blue(pressedColor) + Color.green(pressedColor)) / (3 * 255.0f)
    val darkenPressedColor = if (avg > TRESHOLD) {
      val darken: Float = 1.0f - DARKEN_FACTOR
       Color.rgb((Color.red(pressedColor) * darken).toInt, (Color.green(pressedColor) * darken).toInt, (Color.blue(pressedColor) * darken).toInt)
    } else {
      pressedColor
    }
    val pressed: Int = ColorUtils.injectAlpha(alphaPressed, darkenPressedColor)
    val pressedBgColor: GradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, Array[Int](pressed, pressed))
    pressedBgColor.setShape(GradientDrawable.OVAL)
    val defaultBgColor: GradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, Array[Int](defaultColor, defaultColor))
    defaultBgColor.setShape(GradientDrawable.OVAL)
    val states: StateListDrawable = new StateListDrawable
    states.addState(Array[Int](android.R.attr.state_pressed), pressedBgColor)
    states.addState(Array[Int](android.R.attr.state_focused), pressedBgColor)
    states.addState(Array[Int](-android.R.attr.state_enabled), pressedBgColor)
    states.addState(Array[Int](), defaultBgColor)
    glyphTextView.setBackground(states)
    invalidate()
  }

  def initTextColor(selectedColor: Int): Unit =  {
    var pressedColor: Int = getColor(R.color.text__primary_dark_40)
    var focusedColor: Int = pressedColor
    var enabledColor: Int = getColor(R.color.text__primary_dark)
    var disabledColor: Int = getColor(R.color.text__primary_dark_16)
    if (!ThemeUtils.isDarkTheme(getContext)) {
      pressedColor = getColor(R.color.text__primary_light__40)
      focusedColor = pressedColor
      enabledColor = getColor(R.color.text__primary_light)
      disabledColor = getColor(R.color.text__primary_light_16)
    }
    val colors: Array[Int] = Array(pressedColor, focusedColor, selectedColor, enabledColor, disabledColor)
    val states: Array[Array[Int]] = Array(Array(android.R.attr.state_pressed), Array(android.R.attr.state_focused), Array(android.R.attr.state_selected), Array(android.R.attr.state_enabled), Array(-android.R.attr.state_enabled))
    val colorStateList: ColorStateList = new ColorStateList(states, colors)
    glyphTextView.setTextColor(colorStateList)
  }

  def setSolidTextColor(color: Int): Unit = {
    glyphTextView.setTextColor(color)
  }
}

case class ToolbarSendButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends ToolbarButton(context, attrs, defStyleAttr) with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)
  inflate(R.layout.view_toolbar_send_item)
}

case class ToolbarNormalButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends ToolbarButton(context, attrs, defStyleAttr) with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)
  inflate(R.layout.view_toolbar_item)
}

