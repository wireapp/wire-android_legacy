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
package com.waz.zclient.controllers

import android.content.Context
import com.waz.ZLog
import com.waz.content.GlobalPreferences
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.ui.theme.{OptionsDarkTheme, OptionsLightTheme, OptionsTheme}
import com.waz.zclient.{Injectable, Injector, R}

object ThemeController {
  val Tag: String = ZLog.logTagFor[ThemeController]
}

class ThemeController(implicit injector: Injector, context: Context, ec: EventContext) extends Injectable {

  private val zms = inject[Signal[ZMessaging]]

  val optionsDarkTheme: OptionsTheme = new OptionsDarkTheme(context)
  val optionsLightTheme: OptionsTheme = new OptionsLightTheme(context)

  val darkTheme = zms.flatMap(_.prefs.preference(GlobalPreferences.DarkTheme).signal)

  private var _darkTheme = false
  darkTheme{ _darkTheme = _ }

  private var _previousTheme = false
  darkTheme.head.map{ _previousTheme = _ }(Threading.Ui)

  def setDarkTheme(active: Boolean): Unit = {
    zms.head.flatMap(_.prefs.preference(GlobalPreferences.DarkTheme).update(active))(Threading.Background)
  }

  def toggleDarkTheme(): Unit ={
    setDarkTheme(!_darkTheme)
  }

  def isDarkTheme: Boolean = _darkTheme

  def shouldActivityRestart: Boolean = {
    _darkTheme != _previousTheme
  }

  def activityRestarted(): Unit = {
    _previousTheme = _darkTheme
  }

  def getTheme: Int = if (isDarkTheme) R.style.Theme_Dark else R.style.Theme_Light

  def getThemeDependentOptionsTheme: OptionsTheme = if (isDarkTheme) optionsDarkTheme else optionsLightTheme
}
