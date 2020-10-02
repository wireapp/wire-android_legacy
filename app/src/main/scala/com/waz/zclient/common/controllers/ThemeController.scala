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
package com.waz.zclient.common.controllers

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.waz.content.UserPreferences.DarkTheme
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountManager
import com.waz.threading.Threading
import com.wire.signals.{Signal, SourceSignal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.ui.theme.{OptionsDarkTheme, OptionsLightTheme, OptionsTheme}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

import scala.concurrent.Await
import scala.concurrent.duration._

class ThemeController(implicit injector: Injector, context: Context)
  extends Injectable with DerivedLogTag {
  
  import Threading.Implicits.Background

  val optionsDarkTheme:  OptionsTheme = new OptionsDarkTheme(context)
  val optionsLightTheme: OptionsTheme = new OptionsLightTheme(context)

  lazy val darkThemePref = inject[Signal[AccountManager]].map(_.userPrefs.preference(DarkTheme))

  lazy val darkThemeSet = darkThemePref.flatMap(_.signal).disableAutowiring()

  def setDarkTheme(active: Boolean) =
    darkThemePref.head.flatMap(_ := active)

  def toggleDarkTheme() =
    darkThemePref.head.flatMap(_.mutate(!_))

  def isDarkTheme: Boolean = darkThemeSet.currentValue.contains(true)

  def forceLoadDarkTheme: Int = {
    val set = try {
      Await.result(darkThemeSet.head, 1.seconds)
    } catch {
      case _: Exception => false
    }
    if (set) R.style.Theme_Dark else R.style.Theme_Light
  }

  def getTheme: Int = if (isDarkTheme) R.style.Theme_Dark else R.style.Theme_Light

  def getThemeDependentOptionsTheme: OptionsTheme = if (isDarkTheme) optionsDarkTheme else optionsLightTheme

  lazy val darkTheme = returning(context.getResources.newTheme())(_.applyStyle(R.style.Theme_Dark, true))
  lazy val lightTheme = returning(context.getResources.newTheme())(_.applyStyle(R.style.Theme_Light, true))

  val currentTheme = darkThemeSet.map{ dt => if(dt) Theme.Dark else Theme.Light }

  def getTheme(theme: Theme): Resources#Theme = {
    theme match {
      case Theme.Light => lightTheme
      case Theme.Dark => darkTheme
    }
  }
}

trait ThemeControllingView extends View with ViewHelper {
  val theme: SourceSignal[Option[Theme]] = Signal(None)
}

class ThemeControllingFrameLayout(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ThemeControllingView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
}

trait ThemedView extends View with ViewHelper {

  val currentTheme: SourceSignal[Option[Theme]] = Signal(None)

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    getThemeFromParent(this)(currentTheme ! _)
  }

  private def getThemeFromParent(view: View): Signal[Option[Theme]] = {
    view.getParent match {
      case v: ThemeControllingView => v.theme
      case v: ThemedView => v.currentTheme
      case v: View => getThemeFromParent(v)
      case _ => Signal.const(None)
    }
  }
}

object ThemeController {
  object Theme extends Enumeration {
    val Light, Dark = Value
  }
  type Theme = Theme.Value
}
