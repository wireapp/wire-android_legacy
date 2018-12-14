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
package com.waz.zclient.appentry

import java.util.UUID

import android.os.Bundle
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.impl.ErrorResponse
import com.waz.service.SSOService
import com.waz.zclient.InputDialog.{Event, OnNegativeBtn, OnPositiveBtn, ValidatorResult}
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.utils.ContextUtils._

import scala.concurrent.Future

object SSOFragment {
  val SSODialogTag = "SSO_DIALOG"
}

trait SSOFragment extends FragmentHelper {

  import SSOFragment._
  import com.waz.threading.Threading
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val clipboard   = inject[ClipboardUtils]
  private lazy val ssoService  = inject[SSOService]
  private lazy val spinner  = inject[SpinnerController]

  private lazy val dialogStaff = new InputDialog.Listener with InputDialog.InputValidator {
    override def onDialogEvent(event: Event): Unit = event match {
      case OnNegativeBtn => verbose("Negative")
      case OnPositiveBtn(input) =>
        ssoService.extractUUID(input).foreach(uuid => verifyCode(uuid))
    }

    override def isInputInvalid(input: String): ValidatorResult =
      if (ssoService.isTokenValid(input.trim)) ValidatorResult.Valid
      else ValidatorResult.Invalid()
  }


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    clipboard.primaryClipChanged.onUi { _ => extractTokenAndShowSSODialog() }
  }

  override def onStart(): Unit = {
    super.onStart()
    findChildFragment[InputDialog](SSODialogTag).foreach(_.setListener(dialogStaff).setValidator(dialogStaff))
    extractTokenAndShowSSODialog()
  }

  private def extractTokenFromClipboard: Future[Option[String]] = Future {
    for {
      clipboardText <- clipboard.getPrimaryClipItemsAsText.headOption
      token <- ssoService.extractToken(clipboardText.toString)
    } yield token
  }

  protected def extractTokenAndShowSSODialog(showIfNoToken: Boolean = false): Unit = {
    if (findChildFragment[InputDialog](SSODialogTag).isEmpty) {
      extractTokenFromClipboard
        .filter(_.nonEmpty || showIfNoToken)
        .foreach(showSSODialog)(Threading.Ui)
    }
  }

  protected def showSSODialog(token: Option[String]): Unit = {
    InputDialog
      .newInstance(
        title = R.string.app_entry_sso_dialog_title,
        message = R.string.app_entry_sso_dialog_message,
        inputValue = token,
        inputHint = Some(R.string.app_entry_sso_input_hint),
        validateInput = true,
        disablePositiveBtnOnInvalidInput = true,
        negativeBtn = R.string.app_entry_dialog_cancel,
        positiveBtn = R.string.app_entry_dialog_log_in
      )
      .setListener(dialogStaff)
      .setValidator(dialogStaff)
      .show(getChildFragmentManager, SSODialogTag)
  }

  protected def cancelSSODialog(): Unit = {
    findChildFragment[InputDialog](SSODialogTag).foreach(_.dismissAllowingStateLoss())
  }

  private def verifyCode(code: UUID): Future[Unit] = {
    onVerifyingCode(true)
    ssoService.verifyToken(code).flatMap { result =>
      onVerifyingCode(false)
      import ErrorResponse._
      result match {
        case Right(true) => Future.successful(onSSOConfirm(code.toString))
        case Right(false) => showErrorDialog(R.string.sso_signin_wrong_code_title, R.string.sso_signin_wrong_code_message)
        case Left(ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _)) => showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
        case Left(error) => showConfirmationDialog(getString(R.string.sso_signin_error_title), getString(R.string.sso_signin_error_try_again_message, error.code.toString)).map(_ => ())
      }
    }
  }

  protected def onSSOConfirm(code: String): Unit

  protected def onVerifyingCode(verifying: Boolean): Unit = spinner.showSpinner(verifying)

}
