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
import com.waz.content.UserPreferences.{AppLockEnabled, AppLockForced, AppLockTimeout}
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.teams.FeatureFlagsService
import com.waz.service.{AccountsService, GlobalModule, UserService}
import com.waz.threading.Threading
import com.waz.utils.crypto.AESUtils.{EncryptedBytes, decryptWithAlias, encryptWithAlias}
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.dialogs.NewPasswordDialog
import com.waz.zclient.security.ActivityLifecycleCallback
import com.waz.zclient.{BaseActivity, BuildConfig, Injectable, Injector}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.concurrent.Future

class PasswordController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  import Threading.Implicits.Background

  private val accounts = inject[AccountsService]
  private val prefs = inject[Signal[UserPreferences]]
  private val userAccountsController = inject[UserAccountsController]
  private lazy val featureFlags = inject[Signal[FeatureFlagsService]]
  private val appInBackground = inject[ActivityLifecycleCallback].appInBackground.map(_._1)
  private val ssoPassword = prefs.map(_.preference(UserPreferences.SSOPassword))
  private val ssoPasswordIv = prefs.map(_.preference(UserPreferences.SSOPasswordIv))

  private lazy val sodiumHandler = inject[SodiumHandler]

  // TODO: Remove after everyone migrates to UserPreferences.AppLockEnabled
  appLockPrefMigration()

  appInBackground.foreach {
    case true  =>
      clearPassword()
    case false =>
      if (BuildConfig.APP_LOCK_FEATURE_FLAG) {
        userAccountsController.isTeam.head.foreach {
          case true => featureFlags.head.foreach(_.updateAppLock())
          case false =>
        }
      }
  }

  val ssoEnabled:       Signal[Boolean]                = accounts.isActiveAccountSSO
  val ssoPasswordEmpty: Signal[Boolean]                = ssoPassword.flatMap(_.signal.map(_.isEmpty))
  val appLockEnabled:   Signal[Boolean]                = prefs.flatMap(_.preference(AppLockEnabled).signal)
  val appLockForced:    Signal[Boolean]                = prefs.flatMap(_.preference(AppLockForced).signal)
  lazy val password:    Signal[Option[Password]]       = accounts.activeAccount.map(_.flatMap(_.password)).disableAutowiring()
  val appLockTimeout: Signal[Int] = prefs.flatMap(_.preference(AppLockTimeout).signal).map {
    case None           => BuildConfig.APP_LOCK_TIMEOUT
    case Some(duration) => duration.toSeconds.toInt
  }

  val passwordCheckSuccessful: SourceStream[Unit] = EventStream[Unit]()

  def setPassword(p: Password): Future[Unit] = setPassword(Some(p))
  def clearPassword(): Future[Unit] = setPassword(None)

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
      ssoPref     <- ssoPassword.head
      ssoPassword <- ssoPref()
      _           <- if (pwd.isEmpty && ssoPassword.isEmpty) openNewPasswordDialog(NewPasswordDialog.SetMode)
                     else Future.successful(())
    } yield ()

  def checkPassword(password: Password): Future[Boolean] =
    for {
      isSSO <- ssoEnabled.head
      users <- inject[Signal[UserService]].head
      res   <- if (isSSO)
                 checkSSOPassword(password).map {
                   case true  => Right(())
                   case false => Left("")
                 }
               else
                 users.checkPassword(password)
    } yield res match {
      case Right(_)  => true
      case Left(err) =>
        verbose(l"Check password error: $err")
        false
    }

  private def openNewPasswordDialog(mode: NewPasswordDialog.Mode)(implicit ctx: Context): Future[Int] = Future {
    // The "Change Passcode" version of the dialog is always presented on the dark theme
    // even if the controller says otherwise
    val isDarkTheme = mode == NewPasswordDialog.ChangeMode || inject[ThemeController].isDarkTheme
    val fragment = NewPasswordDialog.newInstance(mode, isDarkTheme)
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
    } yield ()

  private def setSSOPassword(pwd: Password): Future[Unit] =
    for {
      Some(userId)       <- accounts.activeAccountId.head
      hashedPwd          <- hash(pwd.str)
      (encryptedPwd, iv) =  encryptWithAlias(hashedPwd, userId.str).asStrings
      ssoPref            <- ssoPassword.head
      _                  <- ssoPref := Some(encryptedPwd)
      ssoIvPref          <- ssoPasswordIv.head
      _                  <- ssoIvPref := Some(iv)
    } yield ()

  private def checkSSOPassword(pwdToCheck: Password): Future[Boolean] =
    for {
      Some(userId) <- accounts.activeAccountId.head
      hashToCheck  <- hash(pwdToCheck.str)
      ssoPref      <- ssoPassword.head
      ssoPwd       <- ssoPref()
      ssoIvPref    <- ssoPasswordIv.head
      ssoIv        <- ssoIvPref()
    } yield (ssoPwd, ssoIv) match {
      case (Some(encryptedPwd), Some(iv)) =>
        hashToCheck sameElements decryptWithAlias(EncryptedBytes(encryptedPwd, iv), userId.str)
      case _ =>
        false
    }

  private def hash(password: String): Future[Array[Byte]] =
    accounts.activeAccountId.head.collect {
      case Some(id) =>
        sodiumHandler.hash(password, id.str.replace("-", ""))
    }(Threading.Background)

  // TODO: Remove after everyone migrates to UserPreferences.AppLockEnabled
  private def appLockPrefMigration() =
    inject[GlobalPreferences].preference(GlobalPreferences.GlobalAppLockDeprecated).apply().foreach {
      case true =>
      case false =>
        inject[GlobalPreferences].preference(GlobalPreferences.AppLockEnabled).apply().foreach { globalAppLock =>
          prefs.map(_.preference(AppLockEnabled)).head.foreach { userAppLockPref =>
            verbose(l"Migrating the AppLockEnabled preference (set to $globalAppLock) from global to user preferences")
            userAppLockPref := globalAppLock
            inject[GlobalPreferences].preference(GlobalPreferences.GlobalAppLockDeprecated) := true
          }
        }
    }
}
