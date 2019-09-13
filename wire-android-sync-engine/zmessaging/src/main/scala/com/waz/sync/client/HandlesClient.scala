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

import com.waz.api.UsernameValidationError
import com.waz.api.impl.ErrorResponse
import com.waz.model.Handle
import com.waz.sync.client.HandlesClient.UsernameValidation
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json.JSONArray

import scala.util.Try
import scala.util.control.NonFatal

trait HandlesClient {
  def getHandlesValidation(handles: Seq[Handle]): ErrorOrResponse[Seq[UsernameValidation]]
  def isUserHandleAvailable(handle: Handle): ErrorOrResponse[Boolean]
}

class HandlesClientImpl(implicit
                        urlCreator: UrlCreator,
                        httpClient: HttpClient,
                        authRequestInterceptor: AuthRequestInterceptor) extends HandlesClient {

  import HandlesClient._
  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val stringsDeserializer: RawBodyDeserializer[Seq[String]] =
    RawBodyDeserializer[JSONArray].map(array => (0 until array.length()).map(array.getString))

  override def getHandlesValidation(handles: Seq[Handle]): ErrorOrResponse[Seq[UsernameValidation]] = {
    val data = JsonEncoder { o =>
      o.put("handles", JsonEncoder.arrString(handles.take(HandlesClient.MaxHandlesToPost).map(_.toString)))
      o.put("return", 10)
    }

    Request.Post(relativePath = CheckMultipleAvailabilityPath, body = data)
      .withResultType[Seq[String]]
      .withErrorType[ErrorResponse]
      .executeSafe { availableHandles =>
        handles
          .filter(u => availableHandles.contains(u.toString))
          .map(u => UsernameValidation(u.toString, UsernameValidationError.NONE))
      }

  }

  override def isUserHandleAvailable(handle: Handle): ErrorOrResponse[Boolean] = {
    Request.Head(relativePath = checkSingleAvailabilityPath(handle))
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[Response[Unit]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.code == ResponseCode.NotFound)
  }
}

object UsersHandleResponseContent {
  def unapply(response: ResponseContent): Option[Seq[String]] = {
    try {
      response match {
        case JsonArrayResponse(js) => Try(JsonDecoder.array(js, _.getString(_))).toOption
        case _ => None
      }
    } catch {
      case NonFatal(_) =>  None
    }
  }
}

object HandlesClient {
  val CheckMultipleAvailabilityPath = "/users/handles"
  val MaxHandlesToPost = 50

  def checkSingleAvailabilityPath(handle: Handle): String = s"/users/handles/${handle.string}"

  case class UsernameValidation(username: String, reason: UsernameValidationError) {
    def isValid: Boolean = reason == UsernameValidationError.NONE
  }
}

