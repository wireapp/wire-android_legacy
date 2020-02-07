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

import java.net.URL

import androidx.fragment.app.{Fragment, FragmentManager}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{ConnectionErrorCode, TimeoutCode}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model2.transport.responses.DomainSuccessful
import com.waz.service.SSOService
import com.waz.zclient.InputDialog._
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.InputBox.EmailValidator
import com.waz.zclient.utils.BackendController
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

  private def hasToken: Future[Boolean] = userAccountsController.ssoToken.head.map(_.isDefined)

  private lazy val backendController = inject[BackendController]

  private lazy val dialogListener = new InputDialog.Listener {
    override def onTextChanged(text: String): Unit = getSsoDialog.foreach(_.clearError())

    override def onDialogEvent(event: Event): Unit = event match {
      case OnNegativeBtn =>
        dismissSsoDialog()
        hasToken.map(activity.onSSODialogDismissed(_))
      case OnPositiveBtn(input) => verifyUserInput(input)
    }
  }

  private def verifyUserInput(input: String): Unit =
    if (ssoService.isTokenValid(input)) {
      verifySsoCode(input)
    } else if (EmailValidator.isValid(input)) {
      verifyEmail(input)
    } else {
      if (backendController.hasCustomBackend) showInlineSsoError(getString(R.string.enterprise_signin_sso_invalid_input_error))
      else showInlineSsoError(getString(R.string.enterprise_signin_email_sso_invalid_input_error))
    }

  override def onStart(): Unit = {
    super.onStart()
    getSsoDialog.foreach(_.setListener(dialogListener))
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
      case None if getSsoDialog.isEmpty =>
        extractTokenFromClipboard
          .filter(_.nonEmpty || showIfNoToken)
          .foreach(showLoginViaSSOAndEmailDialog)
      case _ =>
    }

  private def getSsoDialog: Option[InputDialog] = findChildFragment[InputDialog](SSODialogTag)

  protected def showLoginViaSSOAndEmailDialog(token: Option[String]): Unit =
    if (getSsoDialog.isEmpty)
      InputDialog.newInstance(
        title = R.string.sso_login_dialog_title,
        message = if (backendController.hasCustomBackend) R.string.sso_login_dialog_message else R.string.email_sso_login_dialog_message ,
        inputHint = Some(R.string.app_entry_sso_input_hint),
        inputValue = token,
        negativeBtn = R.string.app_entry_dialog_cancel,
        positiveBtn = R.string.app_entry_dialog_log_in
      )
        .setListener(dialogListener)
        .show(getChildFragmentManager, SSODialogTag)

  private def verifySsoCode(input: String): Future[Unit] =
    ssoService.extractUUID(input).fold(Future.successful(())) { token =>
      onVerifyingToken(true)
      ssoService.verifyToken(token).flatMap { result =>
        onVerifyingToken(false)
        userAccountsController.ssoToken ! None
        result match {
          case Right(true) =>
            dismissSsoDialog()
            getFragmentManager.popBackStack(SSOWebViewFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            Future.successful(activity.showFragment(SSOWebViewFragment.newInstance(token.toString), SSOWebViewFragment.Tag))
          case Right(false) => showInlineSsoError(getString(R.string.sso_signin_wrong_code_message))
          case Left(errorResponse) => handleVerificationError(errorResponse)
        }
      }
    }


  private def verifyEmail(email: String): Future[Unit] = {
    val domain = ssoService.extractDomain(email)
    ssoService.verifyDomain(domain).flatMap {
      case Right(DomainSuccessful(configFileUrl)) =>
        val isUserLoggedIn = userAccountsController.currentUser.map(_.isDefined).head.isCompleted
        if (!backendController.hasCustomBackend && isUserLoggedIn)
          showInlineSsoError(getString(R.string.enterprise_signin_email_multiple_servers_not_supported))
        else {
          dismissSsoDialog()
          Future.successful(activity.showCustomBackendDialog(new URL(configFileUrl)))
        }
      case Right(_) => showInlineSsoError(getString(R.string.enterprise_signin_domain_not_found_error))
      case Left(err) => handleVerificationError(err)
    }
  }

  private def handleVerificationError(errorResponse: ErrorResponse) = errorResponse match {
    case ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _) =>
      dismissSsoDialog()
      showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
    case error => showInlineSsoError(getString(R.string.sso_signin_error_try_again_message, error.code.toString))
  }

  private def dismissSsoDialog() = getSsoDialog.foreach(_.dismiss())

  private def showInlineSsoError(errorText: String) = Future.successful(getSsoDialog.foreach(_.setError(errorText)))

  protected def activity: AppEntryActivity = getActivity.asInstanceOf[AppEntryActivity]

  protected def onVerifyingToken(verifying: Boolean): Unit =
    inject[SpinnerController].showSpinner(verifying)
}

trait SSOFragmentHandler {
  def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit
  def onSSODialogDismissed(hasToken: Boolean): Unit = {}
}
