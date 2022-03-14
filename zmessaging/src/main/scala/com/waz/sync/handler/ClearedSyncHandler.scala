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

import com.waz.log.LogSE._
import com.waz.api.Message
import com.waz.content.{ConversationStorage, MessagesStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent.Cleared
import com.waz.model._
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.UserService
import com.waz.service.conversation.ConversationsContentUpdaterImpl
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Failure, Success}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.zms.BuildConfig

import scala.concurrent.Future

class ClearedSyncHandler(selfUserId:   UserId,
                         federation:   FederationSupport,
                         convs:        ConversationStorage,
                         convsContent: ConversationsContentUpdaterImpl,
                         users:        UserService,
                         msgs:         MessagesStorage,
                         convSync:     ConversationsSyncHandler,
                         otrSync:      OtrSyncHandler) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  // Returns actual timestamp to use for clear.
  // This is needed to take local (previously unsent) messages into account.
  // Clear may have been scheduled before some messages were sent,
  // in that case we want to use event and time of the last such message
  // (but we need to first send them to get that info).
  private[sync] def getActualClearInfo(convId: ConvId, time: RemoteInstant) =
    msgs.findMessagesFrom(convId, time) map { ms =>
      verbose(l"getActualClearInfo, messages from clear time: $ms")

      val sentMessages = ms.takeWhile(m => m.time == time || m.userId == selfUserId && m.state == Message.Status.SENT)
      val t = sentMessages.lastOption.fold(time)(_.time)
      val archive = sentMessages.length == ms.length // archive only if there is no new or incoming message

      verbose(l"getActualClearInfo = ($t, $archive)")
      (t, archive)
    }

  def postCleared(convId: ConvId, time: RemoteInstant): Future[SyncResult] = {
    verbose(l"postCleared($convId, $time)")

    def postTime(time: RemoteInstant, archive: Boolean) =
      convs.get(convId).flatMap {
        case None =>
          Future.successful(Failure(s"No conversation found for id: $convId"))
        case Some(conv) =>
          val msg = GenericMessage(Uid(), Cleared(conv.remoteId, time))
          val postMsg =
            if (federation.isSupported) {
              otrSync.postQualifiedOtrMessage(ConvId(selfUserId.str), msg, isHidden = true)
            } else {
              otrSync.postOtrMessage(ConvId(selfUserId.str), msg, isHidden = true)
            }
          postMsg.flatMap(_.fold(e => Future.successful(SyncResult(e)), { _ =>
            if (archive) convSync.postConversationState(conv.id, ConversationState(archived = Some(true), archiveTime = Some(time)))
            else Future.successful(Success)
          }))
      }

    getActualClearInfo(convId, time) flatMap { case (t, archive) =>
      postTime(t, archive) flatMap {
        case Success =>
          convsContent.updateConversationCleared(convId, t) map { _ => Success }
        case res =>
          Future successful res
      }
    }
  }
}
