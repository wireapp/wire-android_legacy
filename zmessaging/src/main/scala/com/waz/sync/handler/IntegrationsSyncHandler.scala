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

import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.EventPipeline
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.IntegrationsClient
import com.waz.threading.Threading

import scala.concurrent.Future

trait IntegrationsSyncHandler {
  def addBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncResult]
  def removeBot(cId: ConvId, botId: UserId): Future[SyncResult]
}

//TODO move to ConversationsService?
class IntegrationsSyncHandlerImpl(convs:      ConversationsContentUpdater,
                                  client:     IntegrationsClient,
                                  pipeline:   EventPipeline) extends IntegrationsSyncHandler {
  import Threading.Implicits.Background

  override def addBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncResult] =
    convs.convById(cId).collect { case Some(c) => c.remoteId }.flatMap { rId =>
      client.addBot(rId, pId, iId).future.flatMap {
        case Right(event) =>
          pipeline(Seq(event), None).map(_ => SyncResult.Success)
        case Left(resp@ErrorResponse(502, _, "bad-gateway")) =>
          Future.successful(Failure(resp))
        case Left(error) =>
          Future.successful(SyncResult(error))
      }
    }

  override def removeBot(cId: ConvId, userId: UserId): Future[SyncResult] =
    convs.convById(cId).collect { case Some(c) => c.remoteId }.flatMap { rId =>
      client.removeBot(rId, userId).future.flatMap {
        case Right(event) =>
          pipeline(Seq(event), None).map(_ => SyncResult.Success)
        case Left(error) =>
          Future.successful(SyncResult(error))
      }
    }
}
