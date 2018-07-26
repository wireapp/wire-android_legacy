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
package com.waz.api.impl

import com.waz.sync.client.{JsonObjectResponse, ResponseContent}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.http.HttpClient.CustomErrorConstructor
import com.waz.znet2.http.{BodyDeserializer, HttpClient, ResponseCode}
import org.json.JSONObject

import scala.util.Try

case class ErrorResponse(code: Int, message: String, label: String) extends Throwable {
  /**
    * Returns true if retrying the request will always fail.
    * Non-fatal errors are temporary and retrying the request with the same parameters could eventually succeed.
    */
  def isFatal: Boolean = ResponseCode.isFatal(code)

  // if this error should be reported to hockey
  def shouldReportError: Boolean = isFatal && code != ErrorResponse.CancelledCode && code != ErrorResponse.UnverifiedCode
}

object ErrorResponse {

  val Forbidden = 403
  val InternalErrorCode = 499
  val CancelledCode = 498
  val UnverifiedCode = 497
  val TimeoutCode = 599
  val ConnectionErrorCode = 598
  val UnauthorizedCode = 401

  val InternalError = ErrorResponse(InternalErrorCode, "InternalError", "")
  val Cancelled = ErrorResponse(CancelledCode, "Cancelled", "")
  val Unverified = ErrorResponse(UnverifiedCode, "Unverified", "")
  val PasswordExists = ErrorResponse(Forbidden, "Forbidden", "password-exists")
  val Unauthorized = ErrorResponse(UnauthorizedCode, "Unauthorized", "account-logged-out")

  implicit lazy val Decoder: JsonDecoder[ErrorResponse] = new JsonDecoder[ErrorResponse] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): ErrorResponse = ErrorResponse('code, 'message, 'label)
  }

  implicit lazy val Encoder: JsonEncoder[ErrorResponse] = new JsonEncoder[ErrorResponse] {
    override def apply(v: ErrorResponse): JSONObject = JsonEncoder { o =>
      o.put("code", v.code)
      o.put("message", v.message)
      o.put("label", v.label)
    }
  }

  implicit val errorResponseConstructor: CustomErrorConstructor[ErrorResponse] =
    new CustomErrorConstructor[ErrorResponse] {
      override def constructFrom(error: HttpClient.HttpClientError): ErrorResponse = error match {
        case HttpClient.EncodingError(err) =>
          ErrorResponse.InternalError.copy(message = s"Encoding error: $err")
        case HttpClient.DecodingError(err, response) =>
          ErrorResponse(response.code, label = "Decoding error", message = s"Decoding body error: $err")
        case HttpClient.ConnectionError(err) =>
          ErrorResponse(ErrorResponse.ConnectionErrorCode, message = s"connection error: $err", label = "")
        case HttpClient.UnknownError(err) =>
          ErrorResponse.InternalError.copy(message = s"Unknown error: $err")
      }
    }

  def unapply(resp: ResponseContent): Option[(Int, String, String)] = resp match {
    case JsonObjectResponse(js) => Try((js.getInt("code"), js.getString("message"), js.getString("label"))).toOption
    case _ => None
  }

  def internalError(msg: String) = ErrorResponse(InternalError.code, msg, "internal-error")
}
