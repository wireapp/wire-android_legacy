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
package com.waz.sync.handler

import com.waz.model.ConvId
import com.waz.service.conversation._
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Failure, Success}
import com.waz.sync.client.TypingClient
import com.waz.threading.Threading

import scala.concurrent.Future

class TypingSyncHandler(client: TypingClient, convs: ConversationsContentUpdaterImpl, typingService: TypingService) {

  import Threading.Implicits.Background

  def postTypingState(convId: ConvId, typing: Boolean): Future[SyncResult] = {
    convs.convById(convId) flatMap {
      case Some(conv) =>
        client.updateTypingState(conv.remoteId, isTyping = typing).future map {
          case Right(_)  => Success
          case Left(err) => Failure(err) // don't retry, we don't want to block sync queue
        }

      case None =>
        Future.successful(Failure(s"conversation not found: $convId"))
    }
  }
}
