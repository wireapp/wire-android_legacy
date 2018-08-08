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

import com.waz.model.AccentColor
import com.waz.content.Preferences.PrefKey
import com.waz.content.{GlobalPreferences, UsersStorage}
import com.waz.model.UserId
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}

class AccentColorController(implicit inj: Injector) extends Injectable {
  private lazy val accounts     = inject[AccountsService]
  private lazy val selfUserId   = inject[Signal[Option[UserId]]]
  private lazy val usersStorage = inject[Signal[UsersStorage]]

  private lazy val randomColor =
    inject[GlobalPreferences]
      .preference(PrefKey[Int]("random_accent_color", ZSecureRandom.nextInt(AccentColor.getColors.length)))
      .signal
      .map(AccentColor.getColors.apply(_))

  lazy val accentColor: Signal[AccentColor] = selfUserId.flatMap(
    _.fold(Signal.const(Option.empty[AccentColor]))(accentColor(_))
  ).flatMap {
    case Some(color) => Signal.const(color)
    case None        => randomColor
  }

  lazy val accentColorNoEmpty: Signal[AccentColor] = for {
    Some(selfId) <- selfUserId
    Some(color)  <- accentColor(selfId)
  } yield color

  lazy val colors: Signal[Map[UserId, AccentColor]] = selfUserId.flatMap {
    case None    => Signal.const(Map.empty)
    case Some(_) =>
      for {
        storage  <- usersStorage
        users    <- accounts.accountsWithManagers
        userData <- Signal.sequence(users.map(storage.signal).toSeq: _*)
      } yield userData.map(u =>  u.id -> AccentColor(u.accent)).toMap
  }

  def accentColor(userId: UserId): Signal[Option[AccentColor]] = colors.map(_.get(userId))

  def accentColor(z: ZMessaging): Signal[AccentColor] = z.usersStorage.optSignal(z.selfUserId).map {
    case Some(u) => Some(AccentColor(u.accent))
    case _ => None
  }.flatMap {
    case Some(c) => Signal.const(c)
    case None => randomColor
  }

  def accentColorForJava(callback: AccentColorCallback, ec: EventContext): Unit = {
    accentColor.onUi(callback.color)(ec)
  }

}

trait AccentColorCallback {
  def color(color: AccentColor): Unit
}
