/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.security.checks

import android.content.Context
import com.waz.zclient.{BaseActivity, BuildConfig, R}
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.threading.Threading
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog
import com.waz.zclient.security.SecurityChecklist
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog.{BiometricCancelled, BiometricError, BiometricFailure, BiometricSuccess, PasswordAnswer, PasswordCancelled, PromptAnswer}

import scala.concurrent.{Future, Promise}

class RequestPasswordCheck(pwdCtrl: PasswordController, prefs: UserPreferences)(implicit context: Context)
  extends SecurityChecklist.Check with DerivedLogTag {
  import RequestPasswordCheck._

  private lazy val failedAttempts = prefs(UserPreferences.FailedPasswordAttempts)

  private lazy val passwordCheckSatisfied = Promise[Boolean]

  private lazy val dialog = RequestPasswordDialog(
    title         = ContextUtils.getString(R.string.app_lock_locked_title),
    message       = Some(ContextUtils.getString(R.string.app_lock_locked_message)),
    biometricDesc = Some(ContextUtils.getString(R.string.request_password_biometric_description)),
    onAnswer      = onAnswer,
    isCancellable = false,
    useBiometric  = true
  )

  override def isSatisfied: Future[Boolean] = {
    dialog.show(context.asInstanceOf[BaseActivity])
    passwordCheckSatisfied.future
  }

  private def checkPassword(password: Password): Future[PasswordCheck] = {
    import Threading.Implicits.Background

    if (MaxAttempts == 0 || !BuildConfig.FORCE_APP_LOCK) { // don't count attempts
      pwdCtrl.checkPassword(password).map {
        case true  => PasswordCheckSuccessful
        case false => PasswordCheckFailed
      }
    } else {
      (for {
        fa                 <- failedAttempts()
        maxAttemptsReached =  fa >= (MaxAttempts - 1)
        pwdCheck           <- if (maxAttemptsReached) Future.successful(false) else pwdCtrl.checkPassword(password)
        _                  =  verbose(l"failedAttempts: ${fa + 1}, check: $pwdCheck, max reached: $maxAttemptsReached")
        _                  <- failedAttempts := (if (!pwdCheck && !maxAttemptsReached) fa + 1 else 0)
      } yield (maxAttemptsReached, pwdCheck)).map {
        case (true, _) => MaxAttemptsReached
        case (_, true) => PasswordCheckSuccessful
        case _         => PasswordCheckFailed
      }
    }
  }

  private def onAnswer(answer: PromptAnswer): Unit = answer match {
    case PasswordAnswer(password) => checkPassword(password).foreach {
                                       case PasswordCheckSuccessful  =>
                                         dialog.close()
                                         passwordCheckSatisfied.success(true)
                                       case PasswordCheckFailed =>
                                         dialog.showError(Some(ContextUtils.getString(R.string.request_password_error)))
                                         dialog.clearText()
                                       case PasswordCheckError(err) =>
                                         dialog.showError(Some(err))
                                         dialog.clearText()
                                       case MaxAttemptsReached =>
                                         dialog.close()
                                         passwordCheckSatisfied.success(false)
                                     }(Threading.Ui)
    case PasswordCancelled =>
    case BiometricError(err) =>
      verbose(l"biometric error $err")
    case BiometricSuccess =>
      dialog.close()
      passwordCheckSatisfied.success(true)
    case BiometricFailure =>
    case BiometricCancelled =>
      dialog.cancelBiometric()
  }
}

object RequestPasswordCheck {
  def apply(pwdCtrl: PasswordController, prefs: UserPreferences)(implicit context: Context): RequestPasswordCheck =
    new RequestPasswordCheck(pwdCtrl, prefs)

  val MaxAttempts = BuildConfig.PASSWORD_MAX_ATTEMPTS

  sealed trait PasswordCheck
  case object PasswordCheckSuccessful extends PasswordCheck
  case object PasswordCheckFailed extends PasswordCheck
  case object MaxAttemptsReached extends PasswordCheck
  case class PasswordCheckError(error: String) extends PasswordCheck
}
