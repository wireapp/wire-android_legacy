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
import com.waz.zclient.BuildConfig
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.preferences.dialogs.RequestPasswordDialog
import com.waz.zclient.security.SecurityChecklist
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.R

import scala.concurrent.{Future, Promise}

class RequestPasswordCheck(pwdCtrl: PasswordController, prefs: UserPreferences)(implicit context: Context, evContext: EventContext)
  extends SecurityChecklist.Check with DerivedLogTag {
  import RequestPasswordCheck._

  private lazy val failedAttempts = prefs(UserPreferences.FailedPasswordAttempts)

  override def isSatisfied: Future[Boolean] =
    returning(Promise[Boolean])(showPasswordDialog(_)).future

  private def checkPassword(password: Password, promise: Promise[Boolean]): Unit = {
    import Threading.Implicits.Background
    if (MaxAttempts == 0) { // don't count attempts
      pwdCtrl.checkPassword(password).foreach {
        case true  => promise.success(true)
        case false => showPasswordDialog(promise, Some(ContextUtils.getString(R.string.request_password_error)))
      }
    } else {
      (for {
        fa                 <- failedAttempts()
        maxAttemptsReached =  fa >= (MaxAttempts - 1)
        pwdCheck           <- if (maxAttemptsReached) Future.successful(false) else pwdCtrl.checkPassword(password)
        _                  =  verbose(l"failedAttempts: ${fa + 1}, check: $pwdCheck, max reached: $maxAttemptsReached")
        _                  <- failedAttempts := (if (!pwdCheck && !maxAttemptsReached) fa + 1 else 0)
      } yield (maxAttemptsReached, pwdCheck)).foreach {
        case (true, _) => promise.success(false)
        case (_, true) => promise.success(true)
        case _         => showPasswordDialog(promise, Some(ContextUtils.getString(R.string.request_password_error)))
      }
    }
  }

  private def checkBiometric(result: Boolean, promise: Promise[Boolean]): Unit = if (result) promise.success(true)

  private def showPasswordDialog(promise: Promise[Boolean], error: Option[String] = None): Unit =
    RequestPasswordDialog(
      onPassword    = checkPassword(_, promise),
      error         = error,
      isCancellable = false,
      onBiometric   = Some(checkBiometric(_, promise))
    )

}

object RequestPasswordCheck {
  def apply(pwdCtrl: PasswordController, prefs: UserPreferences)(implicit context: Context, evContext: EventContext): RequestPasswordCheck =
    new RequestPasswordCheck(pwdCtrl, prefs)

  val MaxAttempts = BuildConfig.PASSWORD_MAX_ATTEMPTS
}
