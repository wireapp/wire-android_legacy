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
import com.waz.model.PushToken
import com.waz.model.otr.ClientId
import com.waz.sync.client.PushTokenClient.PushTokenRegistration
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}
import org.json.JSONObject

trait PushTokenClient {
  def postPushToken(token: PushTokenRegistration): ErrorOrResponse[PushTokenRegistration]
  def deletePushToken(token: String): ErrorOrResponse[Unit]
  def getPushTokens(): ErrorOrResponse[Seq[PushTokenRegistration]]
}

class PushTokenClientImpl(implicit
                          urlCreator: UrlCreator,
                          httpClient: HttpClient,
                          authRequestInterceptor: AuthRequestInterceptor) extends PushTokenClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import PushTokenClient._

  override def postPushToken(token: PushTokenRegistration) = {
    Request.Post(relativePath = PushesPath, body = token)
      .withResultType[PushTokenRegistration]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def deletePushToken(token: String) = {
    Request.Delete(relativePath = s"$PushesPath/$token")
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getPushTokens() = {
    Request.Get(relativePath = s"$PushesPath")
      .withResultType[Seq[PushTokenRegistration]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object PushTokenClient {
  val PushesPath = "/push/tokens"

  case class PushTokenRegistration(token: PushToken, senderId: String, clientId: ClientId, transport: String = "GCM")
  object PushTokenRegistration {

    implicit lazy val DecoderSeq: JsonDecoder[Seq[PushTokenRegistration]] = new JsonDecoder[Seq[PushTokenRegistration]] {
      import com.waz.utils.JsonDecoder._
      override def apply(implicit js: JSONObject): Seq[PushTokenRegistration] =
        array[PushTokenRegistration](js.getJSONArray("tokens"))
    }

    implicit lazy val Decoder: JsonDecoder[PushTokenRegistration] = new JsonDecoder[PushTokenRegistration] {
      import com.waz.utils.JsonDecoder._
      override def apply(implicit js: JSONObject): PushTokenRegistration =
        PushTokenRegistration('token, 'app, decodeId[ClientId]('client), 'transport)
    }

    implicit lazy val Encoder: JsonEncoder[PushTokenRegistration] = new JsonEncoder[PushTokenRegistration] {
      override def apply(v: PushTokenRegistration): JSONObject = JsonEncoder { o =>
        o.put("token", v.token)
        o.put("app", v.senderId)
        o.put("transport", v.transport)
        o.put("client", v.clientId.str)
      }
    }

//    implicit val TokenContentEncoder: ContentEncoder[PushTokenRegistration] =
//      ContentEncoder.json[PushTokenRegistration]
  }
}
