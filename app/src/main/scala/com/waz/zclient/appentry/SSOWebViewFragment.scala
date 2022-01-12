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

import android.content.DialogInterface
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils._
import com.waz.utils.wrappers.URI
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.SSOWebViewWrapper.SSOResponse
import com.waz.zclient.appentry.fragments.FirstLaunchAfterLoginFragment
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.utils.{ ContextUtils, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future

class SSOWebViewFragment extends FragmentHelper {
  import Threading.Implicits.Ui

  private lazy val webView = view[WebView](R.id.web_view)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_sso_webview, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {

    val toolbar = view.findViewById[Toolbar](R.id.toolbar)
    toolbar.setNavigationIcon(R.drawable.action_back_dark)
    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = onBackPressed()
    })

    loadWebView()
  }

  private def ssoResponse(loginResult: SSOResponse) = loginResult match {
    case Right((cookie, userId)) =>
      val accountsService = inject[AccountsService]
      accountsService.ssoLogin(userId, cookie).map {
        case Left(error) =>
          ContextUtils.showErrorDialog(EmailError(error))
        case Right((true, hadDB)) =>
          activity.showFragment(FirstLaunchAfterLoginFragment(userId, ssoHadDB = hadDB), FirstLaunchAfterLoginFragment.Tag)
        case _ =>
          for {
            am      <- accountsService.accountManagers.head.map(_.find(_.userId == userId))
            clState <- am.fold2(Future.successful(None), _.getOrRegisterClient().map(_.fold(_ => None, Some(_))))
            _       <- accountsService.setAccount(Some(userId))
          } getActivity.asInstanceOf[AppEntryActivity].onEnterApplication(openSettings = false, initSync = false, clState)
      }
    case Left(error) =>
      ViewUtils.showAlertDialog(
        getActivity,
        getString(R.string.sso_signin_error_title),
        getString(R.string.sso_signin_error_message, error.toString),
        getString(android.R.string.ok),
        new DialogInterface.OnClickListener {
          def onClick(dialog: DialogInterface, which: Int): Unit = {
            onBackPressed()
          }
        },
        true)
  }

  override def onBackPressed(): Boolean = {
    activity.showWelcomeScreen()
    inject[UserAccountsController].ssoToken ! None
    true
  }

  override def onPause(): Unit = {
    super.onPause()
    clearWebView()
  }

  private def loadWebView(): Unit = {
    webView.foreach { webView =>
      val webViewWrapper = new SSOWebViewWrapper(webView, ZMessaging.currentGlobal.backend.baseUrl.toString)
      webViewWrapper.onUrlChanged.onUi { url =>
        webView.findViewById[TextView](R.id.title).setText(Option(URI.parse(url).getHost).getOrElse(""))
      }

      getStringArg(SSOWebViewFragment.SSOCode).foreach(code => webViewWrapper.loginWithCode(code).foreach(ssoResponse))
    }
  }

  private def clearWebView(): Unit = {
    webView.foreach { webView =>
      webView.clearCache(true)
      webView.clearFormData
      webView.clearHistory

    }
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]
}

object SSOWebViewFragment {

  val Tag: String = getClass.getSimpleName
  val SSOCode = "SSO_CODE"

  def newInstance(code: String): SSOWebViewFragment = {
    val bundle = new Bundle()
    bundle.putString(SSOCode, code)
    returning(new SSOWebViewFragment())(_.setArguments(bundle))
  }
}
