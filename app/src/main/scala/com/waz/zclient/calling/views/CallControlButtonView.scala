/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.calling.views

import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.{AttributeSet, TypedValue}
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.waz.utils.returning
import com.waz.zclient.calling.views.CallControlButtonView.ButtonColor
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.theme.{OptionsDarkTheme, OptionsLightTheme}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.RichView

import scala.util.Try

class CallControlButtonView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)
  setGravity(Gravity.CENTER)
  setBackgroundColor(ContextCompat.getColor(getContext, R.color.transparent))

  private val (
    circleIconDimension,
    buttonLabelWidth,
    labelTextSize,
    labelFont
    ) = Try(context.getTheme.obtainStyledAttributes(attrs, R.styleable.CallControlButtonView, 0, 0)).toOption.map { a =>
    returning {
      (
        a.getDimensionPixelSize(R.styleable.CallControlButtonView_circleIconDimension, 0),
        a.getDimensionPixelSize(R.styleable.CallControlButtonView_labelWidth, 0),
        a.getDimensionPixelSize(R.styleable.CallControlButtonView_labelTextSize, 0),
        a.getString(R.styleable.CallControlButtonView_labelFont))
    } (_ => a.recycle())
  }.getOrElse((0, 0, 0, ""))

  private var _isPressed: Boolean = false

  private val buttonView = returning(new GlyphTextView(getContext, null, defStyleAttr)) { b =>
    b.setLayoutParams(new LinearLayout.LayoutParams(circleIconDimension, circleIconDimension))
    b.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__icon_button__text_size))
    b.setGravity(Gravity.CENTER)
    addView(b)
  }

  private val buttonLabelView =
    returning(new TypefaceTextView(getContext, null, R.attr.callingControlButtonLabel)) { b =>
      b.setTextSize(TypedValue.COMPLEX_UNIT_PX, labelTextSize)
      b.setTypeface(labelFont)
      b.setGravity(Gravity.CENTER)

      val params = if (buttonLabelWidth > 0) new LinearLayout.LayoutParams(buttonLabelWidth, WRAP_CONTENT)
      else new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
      params.topMargin = getDimenPx(R.dimen.calling__controls__button__label__margin_top)

      addView(b, params)
    }

  def setButtonPressed(isPressed: Boolean) = if (this._isPressed != isPressed) {
    this._isPressed = isPressed
    if (isPressed) {
      buttonView.setTextColor(new OptionsLightTheme(getContext).getTextColorPrimarySelector)
      buttonView.setBackground(ContextCompat.getDrawable(getContext, R.drawable.selector__icon_button__background__calling_toggled))
    }
    else {
      buttonView.setTextColor(new OptionsDarkTheme(getContext).getTextColorPrimarySelector)
      buttonView.setBackground(ContextCompat.getDrawable(getContext, R.drawable.selector__icon_button__background__calling))
    }
  }

  def setGlyph(glyphId: Int): Unit = buttonView.setText(getResources.getText(glyphId))

  def setText(stringId: Int): Unit = buttonLabelView.setText(getResources.getText(stringId))

  import ButtonColor._
  def setColor(color: ButtonColor) = {

    val (drawable, textColor) = color match {
      case Green       => (R.drawable.selector__icon_button__background__green,   R.color.selector__icon_button__text_color__dark)
      case Red         => (R.drawable.selector__icon_button__background__red,     R.color.selector__icon_button__text_color__dark)
      case Transparent => (R.drawable.selector__icon_button__background__calling, R.color.wire__text_color_primary_dark_selector)
    }

    buttonView.setTextColor(getColorStateList(textColor))
    buttonView.setBackground(getDrawable(drawable))
  }

}

object CallControlButtonView {

  object ButtonColor extends Enumeration {
    val Green, Red, Transparent = Value
  }
  type ButtonColor = ButtonColor.Value

  case class ButtonSettings(glyphId: Int, labelStringId: Int, onClick: () => Unit, color: ButtonColor = ButtonColor.Transparent) {
    def set(button: CallControlButtonView, controlsView: OutgoingControlsView) = {
      button.setGlyph(glyphId)
      button.setText(labelStringId)
      button.setColor(color)
      button.onClick {
        onClick()
        controlsView.callOnClick()
      }
    }
  }
}
