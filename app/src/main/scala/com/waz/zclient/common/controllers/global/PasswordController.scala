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

import android.content.Context
import androidx.fragment.app.FragmentTransaction
import com.waz.content.GlobalPreferences.AppLockEnabled
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.{AccountsService, GlobalModule, UserService}
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.dialogs.NewPasswordDialog
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{BaseActivity, Injectable, Injector}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.concurrent.Future

class PasswordController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  import Threading.Implicits.Background

  private val accounts = inject[AccountsService]
  private val ssoPassword = inject[Signal[UserPreferences]].map(_.preference(UserPreferences.SSOPassword))

  private lazy val sodiumHandler = inject[SodiumHandler]

  val ssoEnabled:       Signal[Boolean] = accounts.isActiveAccountSSO
  val ssoPasswordEmpty: Signal[Boolean] = ssoPassword.flatMap(_.signal.map(_.isEmpty))
  val appLockEnabled:   Signal[Boolean] = inject[GlobalPreferences].preference(AppLockEnabled).signal
  lazy val password:    Signal[Option[Password]] = accounts.activeAccount.map(_.flatMap(_.password)).disableAutowiring()

  val passwordCheckSuccessful: SourceStream[Unit] = EventStream[Unit]()

  def setPassword(p: Password): Future[Unit] = setPassword(Some(p))
  def clearPassword(): Future[Unit] = setPassword(None)

  private def hash(password: String) =
    accounts.activeAccountId.head.collect {
      case Some(id) =>
        sodiumHandler.hash(password, id.str.replace("-", ""))
    }(Threading.Background)

  def changeSSOPassword()(implicit ctx: Context): Future[Unit] =
    for {
      true <- appLockEnabled.head
      true <- ssoEnabled.head
      _    <- openNewPasswordDialog(NewPasswordDialog.ChangeMode)
    } yield ()

  def setSSOPasswordIfNeeded()(implicit ctx: Context): Future[Unit] =
    for {
      true        <- appLockEnabled.head
      true        <- ssoEnabled.head
      pwd         <- password.head
      pref        <- ssoPassword.head
      ssoPassword <- pref()
      _           <- if (pwd.isEmpty && ssoPassword.isEmpty) openNewPasswordDialog(NewPasswordDialog.SetMode)
                     else Future.successful(())
    } yield ()

  private def openNewPasswordDialog(mode: NewPasswordDialog.Mode)(implicit ctx: Context) = Future {
    val fragment = NewPasswordDialog.newInstance(mode)
    ctx.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(fragment, NewPasswordDialog.Tag)
      .addToBackStack(NewPasswordDialog.Tag)
      .commit
  }(Threading.Ui)

  private def setPassword(pwd: Option[Password]): Future[Unit] =
    for {
      Some(accountData) <- accounts.activeAccount.head
      _                 <- inject[GlobalModule].accountsStorage.update(accountData.id, _.copy(password = pwd))
      isSSO             <- ssoEnabled.head
      _                 <- pwd match {
                             case Some(p) if isSSO => setSSOPassword(p)
                             case _                => Future.successful(())
                           }
    } yield {}

  private def setSSOPassword(pwd: Password): Future[Unit] =
    for {
      encryptedPwd <- hash(pwd.str)
      pref         <- ssoPassword.head
      _            <- pref := Some(encryptedPwd)
    } yield {}

  inject[ActivityLifecycleCallback].appInBackground.onUi {
    case (true, _) => clearPassword()
    case _ =>
  }

  def checkPassword(password: Password): Future[Boolean] =
    for {
      isSSO <- ssoEnabled.head
      users <- inject[Signal[UserService]].head
      res   <- if (isSSO) checkSSOPassword(password).map(_ => Right(())) else users.checkPassword(password)
    } yield res match {
      case Right(_)  => true
      case Left(err) =>
        verbose(l"Check password error: $err")
        false
    }

  private def checkSSOPassword(password: Password): Future[Boolean] =
    for {
      pref         <- ssoPassword.head
      ssoPwd       <- pref()
      encryptedPwd <- hash(password.str)
    } yield ssoPwd.contains(encryptedPwd)
}
