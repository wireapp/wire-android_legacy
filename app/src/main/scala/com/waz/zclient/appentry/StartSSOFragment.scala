package com.waz.zclient.appentry

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{ConnectionErrorCode, TimeoutCode}
import com.waz.model2.transport.responses.SSOFound
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.utils.ContextUtils.showErrorDialog

import scala.concurrent.Future

class StartSSOFragment extends SSOFragment {

  import com.waz.threading.Threading.Implicits.Ui

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_start_sso, container, false)

  override def onStart() = {
    super.onStart()
    fetchSsoToken()
  }

  private def fetchSsoToken(): Unit =
    userAccountsController.ssoToken.head.foreach {
      case Some(token) => verifySsoCode(token)
      case None =>
        ssoService.fetchSSO().flatMap {
          case Right(SSOFound(ssoCode)) => startSsoFlow(ssoCode)
          case Right(_)                 => showSsoDialogFuture()
          case Left(ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _)) =>
            showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
          case Left(_)                  => showSsoDialogFuture()
        }
    }

  override def verifySsoCode(input: String): Future[Unit] =
    ssoService.extractUUID(input).fold(Future.successful(())) { token =>
      onVerifyingToken(true)
      ssoService.verifyToken(token).flatMap { result =>
        onVerifyingToken(false)
        userAccountsController.ssoToken ! None
        result match {
          case Right(true)  => goToSsoWebView(token.toString)
          case Right(false) => Future.successful(activity.showCustomBackendLoginScreen())
          case Left(_)      => Future.successful(activity.showCustomBackendLoginScreen())
        }
      }(Threading.Ui)
    }
}

object StartSSOFragment {

  def newInstance() = new StartSSOFragment

  val TAG = "StartSSOFragment"
}
