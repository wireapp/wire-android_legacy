/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.controllers.global

import com.waz.api.impl.{AccentColor, AccentColors}
import com.waz.model.UserData
import com.waz.service.{PreferenceService, ZMessaging}
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}

import scala.util.Random

class AccentColorController(implicit inj: Injector) extends Injectable {
  private lazy val prefService = inject[PreferenceService]

  private val zms = inject[Signal[Option[ZMessaging]]]

  private val self = zms.flatMap {
    case Some(z) => z.usersStorage.optSignal(z.selfUserId)
    case _ => Signal.const[Option[UserData]](None)
  }

  private val randomColorPref = prefService.intPreference("random_accent_color", defaultValue = Random.nextInt(AccentColors.colors.length))

  private val userColor = self.map {
    case Some(s) => Some(AccentColor(s.accent))
    case _ => None
  }

  val accentColor = userColor.flatMap {
    case Some(c) => Signal.const(c)
    case None => randomColorPref.signal.map {
      AccentColors.colors(_)
    }
  }
}
