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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.{AccountsService, GlobalModule, UserService}
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.log.LogUI._
import com.waz.threading.Threading._

import scala.concurrent.Future

class PasswordController(implicit inj: Injector)
  extends Injectable with DerivedLogTag {

  import Threading.Implicits.Background

  private lazy val accounts = inject[AccountsService]
  private lazy val users    = inject[Signal[UserService]]

  lazy val password = accounts.activeAccount.map(_.flatMap(_.password)).disableAutowiring()

  //The password is never saved in the database, this will just update the in-memory version of the current account
  //so that the password is globally correct.
  def setPassword(p: Password): Future[Unit] = setPassword(Some(p))
  def setPassword(p: Option[Password]): Future[Unit] =
    for {
      Some(accountData) <- accounts.activeAccount.head
      _                 <- inject[GlobalModule].accountsStorage.update(accountData.id, _.copy(password = p))
    } yield {}

  inject[ActivityLifecycleCallback].appInBackground.onUi {
    case (true, _) => setPassword(None)
    case _ =>
  }

  def checkPassword(password: Password): Future[Boolean] =
    for {
      users <- users.head
      res   <- users.checkPassword(password)
    } yield res match {
      case Right(_) => true
      case Left(err) =>
        verbose(l"Check password error: $err")
        false
    }
}
