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
package com.waz.client

import com.waz.api._
import com.waz.api.impl.ErrorResponse
import com.waz.model.AccountData.Label
import com.waz.model._
import com.waz.sync.client.AuthenticationManager.Cookie
import com.waz.sync.client.{ErrorOr, LoginClient}
import com.waz.utils.JsonEncoder
import com.waz.utils.Locales._
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request, Response}

trait RegistrationClient {
  def requestPhoneCode(phone: PhoneNumber, login: Boolean, call: Boolean = false): ErrorOr[Unit]
  def requestEmailCode(email: EmailAddress): ErrorOr[Unit] //for now only used for registration
  def requestVerificationEmail(email: EmailAddress): ErrorOr[Unit]
  def verifyRegistrationMethod(method: Either[PhoneNumber, EmailAddress], code: ConfirmationCode, dryRun: Boolean): ErrorOr[Option[(Cookie, Label)]]
  def register(credentials: Credentials, name: Name, teamName: Option[Name]): ErrorOr[(UserInfo, Option[(Cookie, Label)])]
}

class RegistrationClientImpl(implicit
                             urlCreator: UrlCreator,
                             httpClient: HttpClient) extends RegistrationClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import RegistrationClientImpl._
  import com.waz.threading.Threading.Implicits.Background

  override def register(credentials: Credentials, name: Name, teamName: Option[Name]): ErrorOr[(UserInfo, Option[(Cookie, Label)])] = {
    val label = Label()
    val params = JsonEncoder { o =>
      o.put("name", name)
      o.put("locale", bcp47.languageTagOf(currentLocale))
      credentials.addToRegistrationJson(o)
      teamName.foreach { t =>
        o.put("team", JsonEncoder { o2 =>
          o2.put("icon", "default")
          o2.put("name", t)
        })
      }
      o.put("label", label.str)
    }

    Request.Post(relativePath = RegisterPath, body = params)
      .withResultType[Response[UserInfo]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        response.body -> LoginClient.getCookieFromHeaders(response.headers).map(c => (c, label))
      }
      .future
  }

  override def requestPhoneCode(phone: PhoneNumber, login: Boolean, call: Boolean): ErrorOr[Unit] =
    requestCode(Left(phone), login, call)

  override def requestEmailCode(email: EmailAddress): ErrorOr[Unit] =
    requestCode(Right(email))

  //note, login and call only apply to PhoneNumber and are always false for email addresses
  private def requestCode(method: Either[PhoneNumber, EmailAddress], login: Boolean = false, call: Boolean = false): ErrorOr[Unit] = {
    val params = JsonEncoder { o =>
      method.fold(p => o.put("phone", p.str), e => o.put("email",  e.str))
      if (!login) o.put("locale", bcp47.languageTagOf(currentLocale))
      if (call)   o.put("voice_call", call)
    }

    Request.Post(relativePath = if (login) LoginSendPath else ActivateSendPath, body = params)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def requestVerificationEmail(email: EmailAddress): ErrorOr[Unit] = {
    val params = JsonEncoder { o =>
      o.put("email", email.str)
    }
    Request.Post(relativePath = ActivateSendPath, body = params)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def verifyRegistrationMethod(method: Either[PhoneNumber, EmailAddress], code: ConfirmationCode, dryRun: Boolean): ErrorOr[Option[(Cookie, Label)]] = {
    val label = Label()
    val params = JsonEncoder { o =>
      method.fold(p => o.put("phone", p.str), e => o.put("email",  e.str))
      o.put("code",   code.str)
      o.put("dryrun", dryRun)
      if (!dryRun) o.put("label", label.str)
    }

    Request.Post(relativePath = ActivatePath, body = params)
      .withResultType[Response[Unit]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        LoginClient.getCookieFromHeaders(response.headers).map(c => (c, label))
      }
      .future
  }
}

object RegistrationClientImpl {
  val RegisterPath = "/register"
  val ActivatePath = "/activate"
  val ActivateSendPath = "/activate/send"
  val LoginSendPath = "/login/send"
}
