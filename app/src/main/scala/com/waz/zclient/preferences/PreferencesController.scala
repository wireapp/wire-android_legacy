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

import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.wire.signals.Signal
import com.waz.zclient.{Injectable, Injector}

//So that java can access the new preferences from the SE
class PreferencesController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  private val zms = inject[Signal[ZMessaging]]
  private val userPrefs = zms.map(_.userPrefs)

  val sendButtonEnabled = userPrefs.flatMap(_.preference(UserPreferences.SendButtonEnabled).signal).disableAutowiring()

  def isSendButtonEnabled: Boolean = sendButtonEnabled.currentValue.getOrElse(true)
}
