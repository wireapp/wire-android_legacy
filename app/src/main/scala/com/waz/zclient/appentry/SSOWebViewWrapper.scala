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

package com.waz.zclient.appentry

import android.webkit.{WebView, WebViewClient}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.sync.client.AuthenticationManager.Cookie
import com.waz.sync.client.LoginClient
import com.waz.utils.events.EventStream
import com.waz.utils.wrappers.URI
import com.waz.zclient.appentry.SSOWebViewWrapper._
import com.waz.zclient.log.LogUI._

import scala.concurrent.{Future, Promise}
import scala.util.Success


class SSOWebViewWrapper(webView: WebView, backendHost: String) extends DerivedLogTag {

  private var loginPromise = Promise[SSOResponse]()

  val onTitleChanged = EventStream[String]()
  val onUrlChanged = EventStream[String]()

  webView.getSettings.setJavaScriptEnabled(true)

  webView.setWebViewClient(new WebViewClient {
    override def onPageFinished(view: WebView, url: String): Unit = {
      onTitleChanged ! {
        val title = view.getTitle
        Option(URI.parse(title).getHost).filter(_.nonEmpty).getOrElse(title)
      }
      onUrlChanged ! url
      verbose(l"onPageFinished: ${redactedString(url)}")
    }

    override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean = {
      verbose(l"shouldOverrideUrlLoading: ${redactedString(url)}")
      parseURL(url).fold(false) { result =>
        loginPromise.tryComplete(Success(result))
        true
      }
    }
  })

  def loginWithCode(code: String): Future[SSOResponse] = {
    verbose(l"loginWithCode ${redactedString(code)}")
    loginPromise.tryComplete(Success(Left(-1)))
    loginPromise = Promise[SSOResponse]()

    val url = URI.parse(s"$backendHost${LoginClient.InitiateSSOLoginPath(code)}")
      .buildUpon
      .appendQueryParameter("success_redirect", s"$ResponseSchema://success/?$CookieQuery=$$cookie&$UserIdQuery=$$userid")
      .appendQueryParameter("error_redirect", s"$ResponseSchema://error/?$FailureQuery=$$label")
      .build
      .toString

    webView.loadUrl(url)
    loginPromise.future
  }
}

object SSOWebViewWrapper {

  // TODO: Investigate why we can't derive the log tag.
  private implicit val logTag: LogTag = LogTag[SSOWebViewWrapper.type]

  val ResponseSchema = "wire"
  val CookieQuery = "cookie"
  val UserIdQuery = "user"
  val FailureQuery = "failure"

  type SSOResponse = Either[Int, (Cookie, UserId)]

  val SSOErrors = Map(
    "server-error-unsupported-saml" -> 1,
    "bad-success-redirect" -> 2,
    "bad-failure-redirect" -> 3,
    "bad-username" -> 4,
    "bad-upstream" -> 5,
    "server-error" -> 6,
    "not-found" -> 7,
    "forbidden" ->	8,
    "no-matching-auth-req" ->	9,
    "insufficient-permissions" -> 10)

  def parseURL(url: String): Option[SSOResponse] = {
    verbose(l"parseURL ${redactedString(url)}")
    val uri = URI.parse(url)

    if (uri.getScheme.equals(ResponseSchema)) {
      val cookie = Option(uri.getQueryParameter(CookieQuery))
      val userId = Option(uri.getQueryParameter(UserIdQuery))
      val failure = Option(uri.getQueryParameter(FailureQuery))

      (cookie, userId, failure) match {
        case (Some(_ @ LoginClient.CookieHeader(c)), Some(uId), _) => Some(Right(Cookie(c), UserId(uId)))
        case (_, _, Some(f)) => Some(Left(SSOErrors.getOrElse(f, 0)))
        case _ => None
      }
    } else None
  }
}
