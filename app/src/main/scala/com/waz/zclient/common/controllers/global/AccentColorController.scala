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

import com.waz.api.impl.AccentColor
import com.waz.content.UsersStorage
import com.waz.model.AccountId
import com.waz.service.AccountsService
import com.waz.utils.events.Signal
import com.waz.zclient.{Injectable, Injector}

class AccentColorController(implicit inj: Injector) extends Injectable {
  private lazy val accountId = inject[Signal[Option[AccountId]]]

  val accentColor: Signal[com.waz.api.AccentColor] = accountId.flatMap(
    _.fold(Signal.const(Option.empty[com.waz.api.AccentColor]))(accentColor(_))
  ).flatMap {
    case Some(color) => Signal.const(color)
    case None        => inject[Signal[com.waz.api.AccentColor]]
  }

  val accentColorNoEmpty: Signal[com.waz.api.AccentColor] = for {
    Some(accId) <- accountId
    Some(color) <- accentColor(accId)
  } yield color

  def accentColor(accountId: AccountId): Signal[Option[com.waz.api.AccentColor]] = {
    colors.map(_.get(accountId))
  }

  lazy val colors: Signal[Map[AccountId, AccentColor]] = for {
    users          <- inject[AccountsService].loggedInAccounts.map(_.map(acc => acc.id -> acc.userId).toSeq)
    collectedUsers = users.collect { case (accId, Some(userId)) => accId -> userId }
    usersStorage   <- inject[Signal[UsersStorage]]
    userData       <- Signal.sequence(collectedUsers.map {
                        case (accId, userId) => usersStorage.signal(userId).map(accId -> _)
                      }: _*)
  } yield userData.map(u => u._1 -> AccentColor(u._2.accent)).toMap

}
