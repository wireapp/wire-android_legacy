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
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.waz.model.otr.{ClientMismatch, MessageResponse, OtrMessage, QClientMismatch, QMessageResponse, QualifiedOtrMessage}

trait MessagesClient {
  def postMessage(conv: RConvId, content: OtrMessage, ignoreMissing: Boolean, jobId: Option[SyncId]): ErrorOrResponse[MessageResponse]
  def postMessage(conv: RConvQualifiedId, content: QualifiedOtrMessage, jobId: Option[SyncId]): ErrorOrResponse[QMessageResponse]
}

class MessagesClientImpl(implicit
                         urlCreator: UrlCreator,
                         httpClient: HttpClient,
                         authRequestInterceptor: AuthRequestInterceptor) extends MessagesClient with DerivedLogTag {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import MessagesClient._
  import com.waz.threading.Threading.Implicits.Background

  override def postMessage(conv: RConvId, content: OtrMessage, ignoreMissing: Boolean, jobId: Option[SyncId]): ErrorOrResponse[MessageResponse] = {
    jobId.foreach({ j =>
      verbose(l"SSM15<JOB:$j> postMessage 1")
    })
    Request.Post(relativePath = convMessagesPath(conv, ignoreMissing), body = content)
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.PreconditionFailed)
      .withResultType[Response[ClientMismatch]](jobId)
      .withErrorType[ErrorResponse]
      .executeSafe {
        case Response(code, _, body) if code == ResponseCode.PreconditionFailed => MessageResponse.Failure(body)
        case Response(_, _, body) => MessageResponse.Success(body)
      }
  }

  override def postMessage(conv: RConvQualifiedId, content: QualifiedOtrMessage, jobId: Option[SyncId] = None): ErrorOrResponse[QMessageResponse] = {
    jobId.foreach({ j =>
      verbose(l"SSM15<JOB:$j> postMessage 2")
    })
    Request.Post(relativePath = qualifiedConvMessagesPath(conv), body = content)
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.PreconditionFailed)
      .withResultType[Response[QClientMismatch]](jobId)
      .withErrorType[ErrorResponse]
      .executeSafe {
        case Response(code, _, body) if code == ResponseCode.PreconditionFailed => QMessageResponse.Failure(body)
        case Response(_, _, body) => QMessageResponse.Success(body)
      }
  }
}

object MessagesClient extends DerivedLogTag {

  def convMessagesPath(conv: RConvId, ignoreMissing: Boolean): String = {
    val base = s"/conversations/$conv/otr/messages"
    if (ignoreMissing) s"$base?ignore_missing=true" else base
  }

  def qualifiedConvMessagesPath(rConvId: RConvQualifiedId): String =
    s"/conversations/${rConvId.domain}/${rConvId.id.str}/proteus/messages"

}
