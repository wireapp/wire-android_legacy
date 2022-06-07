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
import com.waz.content.UserPreferences._
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.teams.FeatureConfigsService
import com.waz.service.{AccountsService, UserService}
import com.waz.threading.Threading
import com.waz.utils.crypto.AESUtils.{EncryptedBytes, decryptWithAlias, encryptWithAlias}
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.preferences.dialogs.NewPasswordDialog
import com.waz.zclient.security.{ActivityLifecycleCallback, SecurityPolicyChecker}
import com.waz.zclient.{BaseActivity, BuildConfig, Injectable, Injector}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.concurrent.{Future, Promise}

class PasswordController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  import Threading.Implicits.Background

  private lazy val accounts               = inject[AccountsService]
  private lazy val prefs                  = inject[Signal[UserPreferences]]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val featureConfigs         = inject[Signal[FeatureConfigsService]]
  private lazy val customPassword         = prefs.map(_.preference(UserPreferences.CustomPassword))
  private lazy val customPasswordIv       = prefs.map(_.preference(UserPreferences.CustomPasswordIv))
  private lazy val sodiumHandler          = inject[SodiumHandler]

  inject[ActivityLifecycleCallback].appInBackground.map(_._1).foreach {
    case true  =>
      inject[Signal[UserService]].head.foreach(_.clearAccountPassword())
    case false =>
      userAccountsController.isTeam.head.foreach {
        case true  => featureConfigs.head.foreach(_.updateAppLock())
        case false =>
      }
  }

  val ssoManagedPassword:    Signal[Boolean] = accounts.activeAccountHasCompanyManagedPassword
  val customPasswordEmpty:   Signal[Boolean] = customPassword.flatMap(_.signal.map(_.isEmpty))
  val appLockEnabled:        Signal[Boolean] = prefs.flatMap(_.preference(AppLockEnabled).signal)
  val appLockFeatureEnabled: Signal[Boolean] = prefs.flatMap(_.preference(AppLockFeatureEnabled).signal)
  val appLockForced:         Signal[Boolean] = prefs.flatMap(_.preference(AppLockForced).signal)
  val appLockTimeout:        Signal[Int]     = prefs.flatMap(_.preference(AppLockTimeout).signal).map {
    case None           => BuildConfig.APP_LOCK_TIMEOUT
    case Some(duration) => duration.toSeconds.toInt
  }

  val passwordCheckSuccessful: SourceStream[Unit] = EventStream[Unit]()

  def changeCustomPassword()(implicit ctx: Context): Future[Unit] =
    for {
      true <- appLockEnabled.head
      _    <- openNewPasswordDialog(NewPasswordDialog.ChangeMode)
    } yield ()

  def setCustomPasswordIfNeeded()(implicit ctx: Context): Future[Unit] =
    for {
      enabled       <- appLockEnabled.head
      passwordPref  <- customPassword.head
      password      <- passwordPref()
      _             <- if (enabled && password.isEmpty)
                         openNewPasswordDialog(NewPasswordDialog.ChangeInWireMode)
                       else
                         Future.successful(())
    } yield ()

  def clearCustomPassword(): Future[Unit] =
    for {
      passwordPref   <- customPassword.head
      _              <- passwordPref := None
      passwordIvPref <- customPasswordIv.head
      _              <- passwordIvPref := None
    } yield ()

  // TODO: This check is used for the app lock, but some people still use the account password
  //   instead of the custom one as their app lock password. When everyone migrates, it can be simplified.
  def checkPassword(password: Password): Future[Boolean] =
    for {
      users       <- inject[Signal[UserService]].head
      isEmpty     <- customPasswordEmpty.head
      pwdCorrect  <- if (!isEmpty)
                       checkCustomPassword(password)
                     else
                       users.checkAccountPassword(password).map(_.isRight)
    } yield pwdCorrect

  private def checkCustomPassword(pwdToCheck: Password): Future[Boolean] =
    for {
      Some(userId) <- accounts.activeAccountId.head
      hashToCheck  <- hash(pwdToCheck.str)
      ssoPref      <- customPassword.head
      ssoPwd       <- ssoPref()
      ssoIvPref    <- customPasswordIv.head
      ssoIv        <- ssoIvPref()
    } yield (ssoPwd, ssoIv) match {
      case (Some(encryptedPwd), Some(iv)) =>
        hashToCheck sameElements decryptWithAlias(EncryptedBytes(encryptedPwd, iv), userId.str)
      case _ =>
        false
    }

  private def openNewPasswordDialog(mode: NewPasswordDialog.Mode)(implicit ctx: Context) =  {
    // The "Change Passcode" version of the dialog is always presented on the dark themeeven if the controller says otherwise
    val isDarkTheme = mode == NewPasswordDialog.ChangeMode || inject[ThemeController].isDarkTheme
    val dialog = NewPasswordDialog.newInstance(mode, isDarkTheme)
    dialog.show(ctx.asInstanceOf[BaseActivity])
    val operationFinished = Promise[Unit]()
    dialog.onAnswer.foreach {
      case None if mode.cancellable =>
        dialog.close()
        customPasswordEmpty.head.foreach {
          case true => prefs.foreach(_.preference(AppLockEnabled) := false)
          case false =>
        }
        operationFinished.success(())
      case Some(password) =>
        dialog.close()
        setCustomPassword(password).map(_ => operationFinished.success(()))
      case _ =>
    }
    operationFinished.future
  }

  def setCustomPassword(pwd: Password): Future[Unit] =
    for {
      Some(userId)       <- accounts.activeAccountId.head
      hashedPwd          <- hash(pwd.str)
      (encryptedPwd, iv) =  encryptWithAlias(hashedPwd, userId.str).asStrings
      passwordPref       <- customPassword.head
      _                  <- passwordPref := Some(encryptedPwd)
      passwordIvPref     <- customPasswordIv.head
      _                  <- passwordIvPref := Some(iv)
      _                  =  inject[SecurityPolicyChecker].updateBackgroundEntryTimer()
    } yield ()

  private def hash(password: String): Future[Array[Byte]] =
    accounts.activeAccountId.head.collect {
      case Some(id) =>
        sodiumHandler.hash(password, id.str.replace("-", ""))
    }(Threading.Background)
}
