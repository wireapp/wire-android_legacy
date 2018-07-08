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

import com.waz.ZLog.ImplicitTag._
import com.waz.model.AccountData.Password
import com.waz.service.{AccountsService, GlobalModule}
import com.waz.threading.Threading
import com.waz.zclient.{Injectable, Injector}

class PasswordController(implicit inj: Injector) extends Injectable {
  import Threading.Implicits.Background

  val accounts = inject[AccountsService]
  val global   = inject[GlobalModule]
  val password = accounts.activeAccount.map(_.flatMap(_.password)).disableAutowiring()

  //The password is never saved in the database, this will just update the in-memory version of the current account
  //so that the password is globally correct.
  def setPassword(p: Password) =
    for {
      Some(accountData) <- accounts.activeAccount.head
      _ <- global.accountsStorage.update(accountData.id, _.copy(password = Some(p)))
    } yield {}

}
