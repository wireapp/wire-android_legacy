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
package com.waz.sync.client

import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.{EmailAddress, Handle, PhoneNumber, SyncId}
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.{AuthRequestInterceptor, http}
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.wire.signals.CancellableFuture
import org.json.JSONObject
import com.waz.log.LogSE._

trait CredentialsUpdateClient {
  def updateEmail(email: EmailAddress, cookie: Cookie, token: AccessToken): ErrorOrResponse[Unit]
  def clearEmail(): ErrorOrResponse[Unit]

  def updatePhone(phone: PhoneNumber): ErrorOrResponse[Unit]
  def clearPhone(): ErrorOrResponse[Unit]

  def updatePassword(newPassword: Password, currentPassword: Option[Password]): ErrorOrResponse[Unit]
  def updateHandle(handle: Handle): ErrorOrResponse[Unit]

  def hasPassword(): ErrorOrResponse[Boolean]

  def hasMarketingConsent: ErrorOrResponse[Boolean]

  def setMarketingConsent(receiving: Boolean, majorVersion: String, minorVersion: String): ErrorOrResponse[Unit]
}

class CredentialsUpdateClientImpl(implicit
                                  urlCreator: UrlCreator,
                                  httpClient: HttpClient,
                                  authRequestInterceptor: AuthRequestInterceptor)
  extends CredentialsUpdateClient with DerivedLogTag {

  import CredentialsUpdateClientImpl._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import Threading.Implicits.Background

  override def updateEmail(email: EmailAddress, cookie: Cookie, token: AccessToken): ErrorOrResponse[Unit] = {
    val headers = Headers(token.headers ++ cookie.headers)
    val body = JsonEncoder { _.put("email", email.str) }
    Request.Put(relativePath = UpdateEmailPath, headers = headers, body = body)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .flatMap {
        case Left(ErrorResponse.PageNotFound) =>
          warn(l"Unable to use the $UpdateEmailPath endpoint, falling back to $EmailPath")
          Request.Put(relativePath = EmailPath, body = body)
            .withResultType[Unit]()
            .withErrorType[ErrorResponse]
            .executeSafe
        case Left(errorResponse) =>
          CancellableFuture.successful(Left(errorResponse))
        case Right(_) =>
          CancellableFuture(Right(()))
      }
  }

  override def clearEmail(): ErrorOrResponse[Unit] = {
    Request.Delete(relativePath = EmailPath)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updatePhone(phone: PhoneNumber): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = PhonePath, body = JsonEncoder { _.put("phone", phone.str) })
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def clearPhone(): ErrorOrResponse[Unit] = {
    Request.Delete(relativePath = PhonePath)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updatePassword(newPassword: Password, currentPassword: Option[Password]): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = PasswordPath,
        body = JsonEncoder { o =>
          o.put("new_password", newPassword.str)
          currentPassword.map(_.str).foreach(o.put("old_password", _))
        }
      )
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updateHandle(handle: Handle): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = HandlePath, body = JsonEncoder { _.put("handle", handle.toString) })
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def hasPassword(): ErrorOrResponse[Boolean] = {
    Request.Head(relativePath = PasswordPath)
      .withResultType[Response[Unit]]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .map {
        case Right(_) => Right(true)
        case Left(errorResponse) if errorResponse.code == ResponseCode.NotFound => Right(false)
        case Left(errorResponse) => Left(errorResponse)
      }
  }

  override def hasMarketingConsent: ErrorOrResponse[Boolean] = {
    Request.Get(relativePath = ConsentPath)
      .withResultType[JSONObject]()
      .withErrorType[ErrorResponse]
      .executeSafe { json =>
        val results = JsonDecoder.array(json.getJSONArray("results"), {
          case (arr, i) => (arr.getJSONObject(i).getInt("type"), arr.getJSONObject(i).getInt("value"))
        }).toMap
        results.get(ConsentTypeMarketing).contains(1)
      }
  }

  override def setMarketingConsent(receiving: Boolean, majorVersion: String, minorVersion: String): ErrorOrResponse[Unit] = {
    val body = JsonEncoder { o =>
      o.put("type", ConsentTypeMarketing)
      o.put("value", if (receiving) 1 else 0)
      o.put("source", s"Android $majorVersion.$minorVersion")
    }
    Request.Put(relativePath = ConsentPath, body = body)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object CredentialsUpdateClientImpl {
  val PasswordPath = "/self/password"
  val EmailPath = "/self/email"
  val UpdateEmailPath = "/access/self/email"
  val PhonePath = "/self/phone"
  val HandlePath = "/self/handle"

  val ConsentPath = "/self/consent"

  //https://github.com/wireapp/architecture/blob/master/topics/privacy/use_cases/clients/01-change-marketing-consent.md
  //https://github.com/wireapp/architecture/blob/master/topics/privacy/use_cases/clients/02-ask-marketing-consent-at-registration.md
  val ConsentTypeMarketing = 2

}
