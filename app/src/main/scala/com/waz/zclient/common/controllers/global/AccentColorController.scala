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
package com.waz.zclient.common.controllers.global

import com.waz.api.impl.{AccentColor, AccentColors}
import com.waz.content.GlobalPreferences
import com.waz.content.Preferences.PrefKey
import com.waz.service.ZMessaging
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}

class AccentColorController(implicit inj: Injector) extends Injectable {
  private lazy val prefs = inject[GlobalPreferences]

  private val zms = inject[Signal[Option[ZMessaging]]]

  private val randomColorPref = prefs.preference(PrefKey[Int]("random_accent_color", ZSecureRandom.nextInt(AccentColors.colors.length)))

  val accentColor: Signal[com.waz.api.AccentColor] = zms.flatMap {
    case Some(z) => accentColor(z)
    case _ => randomColorPref.signal.map {
      AccentColors.colors(_)
    }
  }

  val accentColorNoEmpty: Signal[com.waz.api.AccentColor] = for {
    Some(z) <- zms
    color <- accentColor(z)
  } yield color

  def accentColor(z: ZMessaging): Signal[com.waz.api.AccentColor] = z.usersStorage.optSignal(z.selfUserId).map {
    case Some(u) => Some(AccentColor(u.accent))
    case _ => None
  }.flatMap {
    case Some(c) => Signal.const(c)
    case None => randomColorPref.signal.map {
      AccentColors.colors(_)
    }
  }
}
