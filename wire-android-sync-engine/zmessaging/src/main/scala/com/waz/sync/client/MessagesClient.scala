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

import java.io.ByteArrayInputStream

import com.google.protobuf.nano.MessageNano
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.sync.client.OtrClient.{ClientMismatch, MessageResponse}
import com.waz.sync.otr.OtrSyncHandler.OtrMessage
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.wire.messages.nano.Otr

trait MessagesClient {
  def postMessage(conv: RConvId, content: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse]
}

class MessagesClientImpl(implicit
                         urlCreator: UrlCreator,
                         httpClient: HttpClient,
                         authRequestInterceptor: AuthRequestInterceptor) extends MessagesClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import MessagesClient._
  import com.waz.threading.Threading.Implicits.Background

  override def postMessage(conv: RConvId, content: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse] =
    Request.Post(relativePath = convMessagesPath(conv, ignoreMissing), body = content)
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.PreconditionFailed)
      .withResultType[Response[ClientMismatch]]
      .withErrorType[ErrorResponse]
      .executeSafe { case Response(code, _, body) =>
        if (code == ResponseCode.PreconditionFailed) MessageResponse.Failure(body)
        else MessageResponse.Success(body)
      }
}

object MessagesClient extends DerivedLogTag {

  def convMessagesPath(conv: RConvId, ignoreMissing: Boolean): String = {
    val base = s"/conversations/$conv/otr/messages"
    if (ignoreMissing) s"$base?ignore_missing=true" else base
  }

  implicit val OtrMessageSerializer: RawBodySerializer[OtrMessage] = RawBodySerializer.create { m =>
    val msg = new Otr.NewOtrMessage
    msg.sender = OtrClient.clientId(m.sender)
    msg.nativePush = m.nativePush
    msg.recipients = m.recipients.userEntries
    m.external foreach { msg.blob = _ }
    m.report_missing.foreach(users => msg.reportMissing = users.map(OtrClient.userId).toArray)

    val bytes = MessageNano.toByteArray(msg)
    RawBody(mediaType = Some(MediaType.Protobuf), () => new ByteArrayInputStream(bytes), dataLength = Some(bytes.length))
  }

}
