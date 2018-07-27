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

import com.waz.ZLog._
import com.waz.model.GenericContent.Reaction
import com.waz.model._
import com.waz.service.conversation.ConversationsContentUpdaterImpl
import com.waz.service.messages.ReactionsService
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncResult
import com.waz.sync.client.MessagesClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.utils._

import scala.concurrent.Future

class ReactionsSyncHandler(client:    MessagesClient,
                           convs:     ConversationsContentUpdaterImpl,
                           service:   ReactionsService,
                           otrSync:   OtrSyncHandler,
                           tracking:  TrackingService) {

  private implicit val logTag: LogTag = logTagFor[ReactionsSyncHandler]
  import com.waz.threading.Threading.Implicits.Background

  def postReaction(id: ConvId, liking: Liking): Future[SyncResult] =
    convs.convById(id) flatMap {
      case Some(conv) =>
        otrSync.postOtrMessage(conv, GenericMessage(Uid(), Reaction(liking.message, liking.action))) flatMap {
          case Right(time) =>
            service.updateLocalReaction(liking, time).map(_ => SyncResult.Success)
          case Left(error) =>
            Future.successful(SyncResult(error))
        }
      case None =>
        tracking.exception(new Exception("postLiking failed, couldn't find conversation"), s"postLiking failed, couldn't find conversation")
        Future.successful(SyncResult.failed())
    }
}
