package com.waz.zclient.appentry
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup, WindowManager}
import android.widget.{Button, TextView}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{ConnectionErrorCode, TimeoutCode}
import com.waz.model2.transport.responses.SSOFound
import com.waz.threading.Threading
import com.waz.utils.events.EventStream
import com.waz.zclient.R
import com.waz.zclient.appentry.DialogErrorMessage.GenericDialogErrorMessage
import com.waz.zclient.utils.BackendController
import com.waz.zclient.utils.ContextUtils.{showErrorDialog, showInfoDialog}

class CustomBackendLoginFragment extends SSOFragment {

  val onEmailLoginClick = EventStream[Unit]()

  private lazy val backendController = inject[BackendController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_custom_backend_login, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    import CustomBackendLoginFragment._

    val titleTextView = findById[TextView](R.id.customBackendTitleTextView)
    val subtitleTextView = findById[TextView]( R.id.customBackendSubtitleTextView)
    val showMoreTextView = findById[TextView](R.id.customBackendShowMoreTextView)
    val welcomeTextView = findById[TextView](R.id.customBackendWelcomeTextView)
    val emailLoginButton = findById[Button](R.id.customBackendEmailLoginButton)
    val ssoLoginButton = findById[Button](R.id.customBackendSsoLoginButton)

    val title = backendController.getStoredBackendConfig.map(_.environment).getOrElse(UNDEFINED_TEXT)
    val configUrl = backendController.customBackendConfigUrl.getOrElse(UNDEFINED_TEXT)

    titleTextView.setText(getString(R.string.custom_backend_info_title, title))
    welcomeTextView.setText(getString(R.string.custom_backend_welcome, title))

    subtitleTextView.setText(configUrl)

    showMoreTextView.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit =
        showInfoDialog(getString(R.string.custom_backend_dialog_info_title, title), configUrl)
    })

    emailLoginButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = onEmailLoginClick ! (())
    })
    ssoLoginButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = fetchSsoToken()
    })
  }

  override def onResume(): Unit = {
    super.onResume()
    activity.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
  }

  override def onPause(): Unit = {
    super.onPause()
    activity.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  }

  private def fetchSsoToken(): Unit =
    userAccountsController.ssoToken.head.foreach {
      case Some(token) => verifySsoCode(token)
      case None =>
        ssoService.fetchSSO().flatMap {
          case Right(SSOFound(ssoCode)) => startSsoFlow(ssoCode)
          case Right(_) => showSsoDialogFuture()
          case Left(ErrorResponse(ConnectionErrorCode | TimeoutCode, _, _)) =>
            showErrorDialog(GenericDialogErrorMessage(ConnectionErrorCode))
          case Left(_) => showSsoDialogFuture()
        }(Threading.Ui)
    } (Threading.Ui)
}

object CustomBackendLoginFragment {
  val TAG = "CustomBackendLoginFragment"
  private val UNDEFINED_TEXT = "N/A"
}
