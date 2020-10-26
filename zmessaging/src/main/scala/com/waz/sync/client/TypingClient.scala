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
import com.waz.model.RConvId
import com.waz.utils.JsonEncoder
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}

trait TypingClient {
  def updateTypingState(id: RConvId, isTyping: Boolean): ErrorOrResponse[Unit]
}

class TypingClientImpl(implicit
                       urlCreator: UrlCreator,
                       httpClient: HttpClient,
                       authRequestInterceptor: AuthRequestInterceptor) extends TypingClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import TypingClient._

  def updateTypingState(id: RConvId, isTyping: Boolean): ErrorOrResponse[Unit] = {
    val payload = JsonEncoder { _.put("status", if (isTyping) "started" else "stopped") }
    Request.Post(relativePath = typingPath(id), body = payload)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object TypingClient {
  def typingPath(id: RConvId): String = s"/conversations/$id/typing"
}
