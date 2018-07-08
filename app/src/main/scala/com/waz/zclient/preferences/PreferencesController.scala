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
package com.waz.zclient.preferences

import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.service.ZMessaging
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}

//So that java can access the new preferences from the SE
class PreferencesController(implicit inj: Injector, ec: EventContext) extends Injectable {

  private val zms = inject[Signal[ZMessaging]]
  private val userPrefs = zms.map(_.userPrefs)
  private val globalPrefs = zms.map(_.userPrefs)

  val sendButtonEnabled = userPrefs.flatMap(_.preference(UserPreferences.SendButtonEnabled).signal).disableAutowiring()
  val analyticsEnabled  = globalPrefs.flatMap(_.preference(GlobalPreferences.AnalyticsEnabled).signal).disableAutowiring()

  def isSendButtonEnabled: Boolean = sendButtonEnabled.currentValue.getOrElse(true)
  def isAnalyticsEnabled: Boolean = analyticsEnabled.currentValue.getOrElse(true)
}
