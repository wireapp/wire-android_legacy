package com.waz.zclient.appentry

import android.os.{Bundle, CountDownTimer}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ProgressBar, TextView}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{ConnectionErrorCode, TimeoutCode}
import com.waz.model2.transport.responses.SSOFound
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.utils.BackendController
import com.waz.zclient.utils.ContextUtils.showErrorDialog

import scala.concurrent.Future

class StartSSOFragment extends SSOFragment {

  import com.waz.threading.Threading.Implicits.Ui

  private val TIME_TO_WAIT: Int = 5000
  private val COUNTDOWN_INTERVAL: Int = 1000
  private val FULL_PERCENTAGE: Int = 100
  private lazy val progressBar = view[ProgressBar](R.id.startSsoProgressBar)
  private lazy val linkTextView = view[TextView](R.id.startSsoLinkTextView)
  private lazy val backendController = inject[BackendController]

  private lazy val timer = new CountDownTimer(TIME_TO_WAIT, COUNTDOWN_INTERVAL) {
    def onTick(millisUntilFinished: Long): Unit = {
      val progress: Double = ((TIME_TO_WAIT - millisUntilFinished).toDouble / TIME_TO_WAIT.toDouble) * FULL_PERCENTAGE
      updateProgressBar(progress)
    }

    def onFinish(): Unit = {
      fetchSsoToken()
      updateProgressBar(FULL_PERCENTAGE)
    }
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_start_sso, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    showCustomBackendLink()
    timer.start()
  }

  private def fetchSsoToken(): Unit =
    userAccountsController.ssoToken.head.foreach {
      case Some(token) => verifyCode(token)
      case None =>
        ssoService.fetchSSO().flatMap {
          case Right(SSOFound(ssoCode)) => startSsoFlow(ssoCode, fromStart = true)
          case Right(_)                 => Future.successful(activity.showCustomBackendLoginScreen())
          case Left(ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _)) =>
            showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
          case Left(_)                  => Future.successful(activity.showCustomBackendLoginScreen())
        }
    }

  private def verifyCode(input: String): Future[Unit] =
    ssoService.extractUUID(input).fold(Future.successful(())) { token =>
      onVerifyingToken(true)
      ssoService.verifyToken(token).flatMap { result =>
        onVerifyingToken(false)
        userAccountsController.ssoToken ! None
        result match {
          case Right(true)  => goToSsoWebView(token.toString, fromStart = true)
          case Right(false) => Future.successful(activity.showCustomBackendLoginScreen())
          case Left(_)      => Future.successful(activity.showCustomBackendLoginScreen())
        }
      }(Threading.Ui)
    }

  private def updateProgressBar(progress: Double): Unit = progressBar.foreach(_.setProgress(progress.toInt))

  private def showCustomBackendLink(): Unit = linkTextView.foreach(
    _.setText(backendController.customBackendConfigUrl.getOrElse(""))
  )
}

object StartSSOFragment {

  def newInstance() = new StartSSOFragment

  val TAG = "StartSSOFragment"
}
