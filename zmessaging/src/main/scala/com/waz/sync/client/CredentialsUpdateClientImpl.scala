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

import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.model.AccountData.Password
import com.waz.model.{EmailAddress, Handle, PhoneNumber}
import com.waz.service.BackendConfig
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http._
import org.json.JSONObject

import scala.concurrent.Future

trait CredentialsUpdateClient {

  def updateEmail(email: EmailAddress): ErrorOrResponse[Unit]
  def clearEmail(): ErrorOrResponse[Unit]

  def updatePhone(phone: PhoneNumber): ErrorOrResponse[Unit]
  def clearPhone(): ErrorOrResponse[Unit]

  def updatePassword(newPassword: Password, currentPassword: Option[Password]): ErrorOrResponse[Unit]
  def updateHandle(handle: Handle): ErrorOrResponse[Unit]

  def hasPassword(): ErrorOrResponse[Boolean]

  def hasMarketingConsent: Future[Boolean] //TODO Why not CancelableFuture?

  def setMarketingConsent(receiving: Boolean, majorVersion: String, minorVersion: String): ErrorOrResponse[Unit]
}

class CredentialsUpdateClientImpl(implicit
                                  private val backendConfig: BackendConfig,
                                  private val httpClient: HttpClient,
                                  private val authRequestInterceptor: AuthRequestInterceptor)
  extends CredentialsUpdateClient {

  import BackendConfig.backendUrl
  import CredentialsUpdateClientImpl._
  import HttpClient.dsl._
  import Threading.Implicits.Background
  private implicit val logTag: LogTag = logTagFor[CredentialsUpdateClientImpl]

  override def updateEmail(email: EmailAddress): ErrorOrResponse[Unit] = {
    val request = Request.create(
      url = backendUrl(EmailPath),
      method = Method.Put,
      body = JsonEncoder { _.put("email", email.str) }
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def clearEmail(): ErrorOrResponse[Unit] = {
    val request = Request.withoutBody(
      url = backendUrl(EmailPath),
      method = Method.Delete
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updatePhone(phone: PhoneNumber): ErrorOrResponse[Unit] = {
    val request = Request.create(
      url = backendUrl(PhonePath),
      method = Method.Put,
      body = JsonEncoder { _.put("phone", phone.str) }
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def clearPhone(): ErrorOrResponse[Unit] = {
    val request = Request.withoutBody(
      url = backendUrl(PhonePath),
      method = Method.Delete
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updatePassword(newPassword: Password, currentPassword: Option[Password]): ErrorOrResponse[Unit] = {
    val request = Request.create(
      url = backendUrl(PasswordPath),
      method = Method.Put,
      body = JsonEncoder { o =>
        o.put("new_password", newPassword.str)
        currentPassword.map(_.str).foreach(o.put("old_password", _))
      }
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updateHandle(handle: Handle): ErrorOrResponse[Unit] = {
    val request = Request.create(
      url = backendUrl(HandlePath),
      method = Method.Put,
      body = JsonEncoder { _.put("handle", handle.toString) }
    )

    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def hasPassword(): ErrorOrResponse[Boolean] = {
    val request = Request.withoutBody(
      url = backendUrl(PasswordPath),
      method = Method.Head
    )

    Prepare(request)
      .withResultType[Response[Unit]]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map {
        case Right(_) => Right(true)
        case Left(errorResponse) if errorResponse.code == ResponseCode.NotFound => Right(false)
        case Left(errorResponse) => Left(errorResponse)
      }
  }

  override def hasMarketingConsent: Future[Boolean] = {
    val request = Request.withoutBody(url = backendUrl(ConsentPath))
    Prepare(request)
      .withResultType[JSONObject]
      .execute
      .map { json =>
        val results = JsonDecoder.array(json.getJSONArray("results"), {
          case (arr, i) => (arr.getJSONObject(i).getInt("type"), arr.getJSONObject(i).getInt("value"))
        }).toMap
        results.get(ConsentTypeMarketing).contains(1)
      }
      .recover { case _ => false }
      .future
  }

  override def setMarketingConsent(receiving: Boolean, majorVersion: String, minorVersion: String): ErrorOrResponse[Unit] = {
    val body = JsonEncoder { o =>
      o.put("type", ConsentTypeMarketing)
      o.put("value", if (receiving) 1 else 0)
      o.put("source", s"Android $majorVersion.$minorVersion")
    }
    val request = Request.create(url = backendUrl(ConsentPath), method = Method.Put, body = body)
    Prepare(request)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object CredentialsUpdateClientImpl {
  val PasswordPath = "/self/password"
  val EmailPath = "/self/email"
  val PhonePath = "/self/phone"
  val HandlePath = "/self/handle"

  val ConsentPath = "/self/consent"

  //https://github.com/wireapp/architecture/blob/master/topics/privacy/use_cases/clients/01-change-marketing-consent.md
  //https://github.com/wireapp/architecture/blob/master/topics/privacy/use_cases/clients/02-ask-marketing-consent-at-registration.md
  val ConsentTypeMarketing = 2

}
