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

import android.app.FragmentManager
import androidx.fragment.app.Fragment
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{ConnectionErrorCode, TimeoutCode}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model2.transport.responses.DomainSuccessful
import com.waz.service.SSOService
import com.waz.zclient.InputDialog._
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.utils.ContextUtils._

import scala.concurrent.Future

object SSOFragment {
  val SSODialogTag = "SSO_DIALOG"
}

trait SSOFragment extends FragmentHelper with DerivedLogTag {
  import SSOFragment._
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val ssoService             = inject[SSOService]
  private lazy val userAccountsController = inject[UserAccountsController]

  private def hasToken(): Future[Boolean] = userAccountsController.ssoToken.head.map(_.isDefined)

  private lazy val inputValidator = EnterpriseLoginInputValidator(ssoService, "Error!!!") //TODO change error text

  private lazy val dialogStaff = new InputDialog.Listener {
    override def onDialogEvent(event: Event): Unit = event match {
      case OnNegativeBtn => hasToken().map(activity.onSSODialogDismissed(_))
      case OnPositiveBtn(input) => verifyUserInput(input)
    }
  }

  private def verifyUserInput(input: String): Unit =
    if (inputValidator.isSsoInput(input)) {
      verifySsoCode(input)
    } else if (inputValidator.isEmailInput(input)) {
      verifyEmail(input)
    } else {
      throw new IllegalStateException("User should not be able to click the button without a valid input")
    }

  override def onStart(): Unit = {
    super.onStart()
    findChildFragment[InputDialog](SSODialogTag).foreach(_.setListener(dialogStaff).setValidator(inputValidator))
    extractTokenAndShowSSODialog(showSsoByDefault)
  }

  private def extractTokenFromClipboard: Future[Option[String]] = Future {
    for {
      clipboardText <- inject[ClipboardUtils].getPrimaryClipItemsAsText.headOption
      token         <- ssoService.extractToken(clipboardText.toString)
    } yield token
  }

  protected def showSsoByDefault = false

  protected def extractTokenAndShowSSODialog(showIfNoToken: Boolean = false): Unit =
    userAccountsController.ssoToken.head.foreach {
      case Some(token) => verifySsoCode(token)
      case None if findChildFragment[InputDialog](SSODialogTag).isEmpty =>
        extractTokenFromClipboard
          .filter(_.nonEmpty || showIfNoToken)
          .foreach(showSSODialog)
      case _ =>
    }

  private def getSsoDialog: Option[InputDialog] = findChildFragment[InputDialog](SSODialogTag)

  protected def showSSODialog(token: Option[String]): Unit =
    if (getSsoDialog.isEmpty)
      InputDialog.newInstance(
        title = R.string.sso_login_dialog_title,
        message = R.string.sso_login_dialog_message,
        inputHint = Some(R.string.app_entry_sso_input_hint),
        inputValue = token,
        validateInput = true,
        disablePositiveBtnOnInvalidInput = true,
        negativeBtn = R.string.app_entry_dialog_cancel,
        positiveBtn = R.string.app_entry_dialog_log_in
      )
        .setListener(dialogStaff)
        .setValidator(inputValidator)
        .show(getChildFragmentManager, SSODialogTag)

  private def verifySsoCode(input: String): Future[Unit] =
    ssoService.extractUUID(input).fold(Future.successful(())) { token =>
      onVerifyingToken(true)
      ssoService.verifyToken(token).flatMap { result =>
        onVerifyingToken(false)
        userAccountsController.ssoToken ! None
        getSsoDialog.foreach(_.dismiss()) //TODO: might show some errors in the dialog
        result match {
          case Right(true) =>
            getFragmentManager.popBackStack(SSOWebViewFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            Future.successful(activity.showFragment(SSOWebViewFragment.newInstance(token.toString), SSOWebViewFragment.Tag))
          case Right(false) =>
            showErrorDialog(R.string.sso_signin_wrong_code_title, R.string.sso_signin_wrong_code_message)
          case Left(errorResponse) => handleVerificationError(errorResponse)
        }
      }
    }

  private def verifyEmail(email: String): Future[Unit] = {
    val domain = ssoService.extractDomain(email)
    ssoService.verifyDomain(domain).flatMap {
      case Right(DomainSuccessful(configFileUrl)) =>
        getSsoDialog.foreach(_.dismiss())
        Future.successful(()) //TODO: save config file and continue flow
      case Right(_) => Future.successful(
          getSsoDialog.foreach(_.setError(getString(R.string.enterprise_signin_domain_not_found_error)))
      )
      case Left(err) =>
        getSsoDialog.foreach(_.dismiss())
        handleVerificationError(err)
    }
  }

  private def handleVerificationError(errorResponse: ErrorResponse) = errorResponse match {
    case ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _) =>
      showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
    case error =>
      inject[AccentColorController].accentColor.head.flatMap { color =>
        showConfirmationDialog(
          title = getString(R.string.sso_signin_error_title),
          msg   = getString(R.string.sso_signin_error_try_again_message, error.code.toString),
          color = color
        )
      }.map(_ => ())
  }

  protected def activity: SSOFragmentHandler = getActivity.asInstanceOf[SSOFragmentHandler]

  protected def onVerifyingToken(verifying: Boolean): Unit =
    inject[SpinnerController].showSpinner(verifying)
}

trait SSOFragmentHandler {
  def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit
  def onSSODialogDismissed(hasToken: Boolean): Unit = {}
}
