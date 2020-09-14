/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz
import java.util.concurrent.TimeUnit

import com.waz.api.{Credentials, EmailCredentials}
import com.waz.content.AccountStorage
import com.waz.model.AccountData.Password
import com.waz.model.{AccountData, EmailAddress}
import com.waz.service.AccountsService
import com.waz.service.assets.UriHelper
import com.waz.sync.client.{AuthenticationManager, LoginClient, LoginClientImpl}
import com.waz.utils.TestUriHelper
import com.waz.znet2.http.HttpClient
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.{AuthRequestInterceptorImpl, HttpClientOkHttpImpl}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

trait AuthenticationConfig {

  val testCredentials: Credentials = EmailCredentials(EmailAddress("mykhailo+5@wire.com"), Password("123456789"))

  val BackendUrl: String = "https://staging-nginz-https.zinfra.io"

  implicit lazy val HttpClient: HttpClient = HttpClientOkHttpImpl(enableLogging = true)

  implicit lazy val urlCreator: UrlCreator = UrlCreator.simpleAppender(() => BackendUrl)

  private val LoginClient: LoginClient = new LoginClientImpl()

  val accountsService: AccountsService
  val accountStorage: AccountStorage

  lazy val AuthenticationManager: AuthenticationManager = {
    val loginResult = Await.result(LoginClient.login(testCredentials), new FiniteDuration(1, TimeUnit.MINUTES)) match {
      case Right(result) => result
      case Left(errResponse) => throw errResponse
    }

    val userInfo = Await.result(LoginClient.getSelfUserInfo(loginResult.accessToken), new FiniteDuration(1, TimeUnit.MINUTES)) match {
      case Right(result) => result
      case Left(errResponse) => throw errResponse
    }

    val testAccountData = AccountData(
      id = userInfo.id,
      teamId = userInfo.teamId,
      cookie = loginResult.cookie.get,
      accessToken = Some(loginResult.accessToken)
    )

    setUpAccountData(testAccountData)

    new AuthenticationManager(userInfo.id, accountsService, accountStorage, LoginClient)
  }

  implicit lazy val authRequestInterceptor: AuthRequestInterceptorImpl = new AuthRequestInterceptorImpl(AuthenticationManager, HttpClient)

  lazy val uriHelper: UriHelper = new TestUriHelper

  def setUpAccountData(accountData: AccountData): Unit

}
