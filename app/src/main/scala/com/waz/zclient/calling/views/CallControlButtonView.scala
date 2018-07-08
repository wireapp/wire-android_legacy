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
package com.waz.zclient.calling.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.{FrameLayout, LinearLayout}
import com.waz.utils.returning
import com.waz.zclient.calling.views.CallControlButtonView.ButtonColor
import com.waz.zclient.common.controllers.{ThemeController, ThemedView}
import com.waz.zclient.paintcode.GenericStyleKitView
import com.waz.zclient.paintcode.StyleKitView.StyleKitDrawMethod
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getStyledDrawable, _}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import com.waz.ZLog.ImplicitTag._
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, RefreshingSignal, Signal}
import com.waz.zclient.common.controllers.ThemeController.Theme

import scala.concurrent.Future
import scala.util.Try

class CallControlButtonView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper with ThemedView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val themeController  = inject[ThemeController]

  private val otherColor       = Signal(Option.empty[ButtonColor])

  private val enabledChanged   = EventStream[Boolean]()
  private val enabledSignal    = RefreshingSignal(Future{ isEnabled }(Threading.Ui), enabledChanged)

  private val activatedChanged = EventStream[Boolean]()
  private val activatedSignal  = RefreshingSignal(Future{ isActivated }(Threading.Ui), activatedChanged)

  inflate(R.layout.call_button_view)

  setOrientation(LinearLayout.VERTICAL)
  setGravity(Gravity.CENTER)

  private val iconDimension = Try(context.getTheme.obtainStyledAttributes(attrs, R.styleable.CallControlButtonView, 0, 0)).toOption.map { a =>
    returning { a.getDimensionPixelSize(R.styleable.CallControlButtonView_iconDimension, 0) }(_ => a.recycle())
  }.filter(_ != 0)

  private val buttonLabelView  = findById[TypefaceTextView](R.id.text)
  private val buttonBackground = findById[FrameLayout](R.id.icon_background)
  private val iconView         = returning(findById[GenericStyleKitView](R.id.icon)) { icon =>
    iconDimension.foreach { size =>
      icon.getLayoutParams.height = size
      icon.getLayoutParams.width = size
    }
  }

  (for {
    otherColor <- otherColor
    theme      <- currentTheme.map(_.getOrElse(Theme.Light))
  } yield
    otherColor match {
      case Some(ButtonColor.Green) => getDrawable(R.drawable.selector__icon_button__background__green)
      case Some(ButtonColor.Red)   => getDrawable(R.drawable.selector__icon_button__background__red)
      case _  =>
        getStyledDrawable(R.attr.callButtonBackground, themeController.getTheme(theme))
          .getOrElse(getDrawable(R.drawable.selector__icon_button__background__calling))
    }
  ).onUi(buttonBackground.setBackground(_))

  (for {
    otherColor <- otherColor
    theme      <- currentTheme.map(_.getOrElse(Theme.Light))
    enabled    <- enabledSignal
    activated  <- activatedSignal
  } yield (otherColor, theme, enabled, activated)).onUi {
    case (Some(_), th, _, _) =>
      iconView.setColor(getColor(R.color.white))
      buttonLabelView.setTextColor(getStyledColor(R.attr.wirePrimaryTextColor, themeController.getTheme(th)))
    case (None, th, enabled, activated) =>
      val resTheme = themeController.getTheme(th)
      val iconColor =
        if (!enabled && activated) getStyledColor(R.attr.callIconDisabledActivatedColor, resTheme)
        else if (!enabled) getStyledColor(R.attr.callIconDisabledColor, resTheme)
        else if (activated) getStyledColor(R.attr.wirePrimaryTextColorReverted, resTheme)
        else getStyledColor(R.attr.wirePrimaryTextColor, resTheme)
      val textColor = if (!enabled) getStyledColor(R.attr.callTextDisabledColor, resTheme)
        else getStyledColor(R.attr.wirePrimaryTextColor, resTheme)
      buttonLabelView.setTextColor(textColor)
      iconView.setColor(iconColor)
  }

  override def setEnabled(enabled: Boolean): Unit = {
    super.setEnabled(enabled)
    this.dispatchSetEnabled(enabled)
    enabledChanged ! enabled
  }

  override def setActivated(activated: Boolean): Unit = {
    super.setActivated(activated)
    activatedChanged ! activated
  }

  def setText(stringId: Int): Unit = buttonLabelView.setText(getResources.getText(stringId))

  def set(icon: StyleKitDrawMethod, labelStringId: Int, onClick: () => Unit, forceColor: Option[ButtonColor] = None): Unit = {
    iconView.setOnDraw(icon)
    setText(labelStringId)
    otherColor ! forceColor
    this.onClick { onClick() }
  }

}

object CallControlButtonView {

  object ButtonColor extends Enumeration {
    val Green, Red = Value
  }
  type ButtonColor = ButtonColor.Value

}
