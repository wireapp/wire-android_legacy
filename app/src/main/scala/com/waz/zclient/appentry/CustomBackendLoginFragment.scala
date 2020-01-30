package com.waz.zclient.appentry
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{Button, TextView}
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils.showInfoDialog

class CustomBackendLoginFragment extends SSOFragment {

  private var titleTextView : TextView = _
  private var subtitleTextView : TextView = _
  private var showMoreTextView : TextView = _

  private var welcomeTextView : TextView = _

  private var emailLoginButton : Button = _
  private var ssoLoginButton : Button = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_custom_backend_login, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    import CustomBackendLoginFragment._
    initViews(view)

    val title = getArguments.getString(KEY_TITLE, UNDEFINED_TEXT)
    titleTextView.setText(title)
    welcomeTextView.setText(getString(R.string.custom_backend_welcome, title))

    val configUrl = getArguments.getString(KEY_CONFIG_URL, UNDEFINED_TEXT)
    subtitleTextView.setText(configUrl)

    showMoreTextView.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit =
        showInfoDialog(getString(R.string.custom_backend_dialog_info_title, title), configUrl)
    })

    emailLoginButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit =
        activity.asInstanceOf[CustomBackendLoginHandler].showEmailSignInForCustomBackend()
    })
    ssoLoginButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = extractTokenAndShowSSODialog(showIfNoToken = true)
    })
  }

  private def initViews(rootView: View): Unit = {
    titleTextView = findById[TextView](rootView, R.id.customBackendTitleTextView)
    subtitleTextView = findById[TextView](rootView, R.id.customBackendSubtitleTextView)
    showMoreTextView = findById[TextView](rootView, R.id.customBackendShowMoreTextView)

    welcomeTextView = findById[TextView](rootView, R.id.customBackendWelcomeTextView)

    emailLoginButton = findById[Button](rootView, R.id.customBackendEmailLoginButton)
    ssoLoginButton = findById[Button](rootView, R.id.customBackendSsoLoginButton)
  }
}

trait CustomBackendLoginHandler {
  def showEmailSignInForCustomBackend(): Unit
}

object CustomBackendLoginFragment {
  val TAG = "CustomBackendLoginFragment"

  private val UNDEFINED_TEXT = "N/A"
  private val KEY_TITLE = "title"
  private val KEY_CONFIG_URL = "configUrl"

  def newInstance(title: Option[String], configUrl: Option[String]) =
    returning(new CustomBackendLoginFragment()) { f =>
      val bundle = new Bundle()
      title.foreach(bundle.putString(KEY_TITLE, _))
      configUrl.foreach(bundle.putString(KEY_CONFIG_URL, _))
      f.setArguments(bundle)
    }
}
