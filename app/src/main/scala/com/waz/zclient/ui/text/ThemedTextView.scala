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
package com.waz.zclient.ui.text

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.{ThemeController, ThemedView}
import com.waz.zclient.ui.text.ThemedTextView._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class ThemedTextView(val context: Context, val attrs: AttributeSet, val defStyle: Int)
  extends AppCompatTextView(context, attrs, defStyle)
    with ViewHelper
    with ThemedView
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val a: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.ThemedTextView, 0, 0)
  private val themedColor: Option[Int] = Option(a.getInt(R.styleable.ThemedTextView_themedColor, 0)).filter(_ != 0)
  a.recycle()

  private var forcedTheme = Option.empty[Theme]

  currentTheme.collect{ case Some(t) => t }.onUi(setTheme)

  private def setTheme(theme: Theme): Unit = {
    val finalTheme = forcedTheme.getOrElse(theme)
    themedColor.collect {
      case PrimaryColorIndex => R.attr.wirePrimaryTextColor
      case SecondaryColorIndex => R.attr.wireSecondaryTextColor
    }.foreach { colorId =>
      setTextColor(getStyledColor(colorId, inject[ThemeController].getTheme(finalTheme)))
    }
  }

  def forceTheme(theme: Option[Theme]): Unit = {
    forcedTheme = theme
    forcedTheme.orElse(currentTheme.currentValue.flatten).foreach(setTheme)
  }
}

object ThemedTextView {
  val PrimaryColorIndex = 1
  val SecondaryColorIndex = 2
}

