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
package com.waz.service.messages

import com.waz.api.Message.Status.DELIVERED
import com.waz.api.Message.Type._
import com.waz.content.{MessagesStorage, ReadReceiptsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.sync.ReceiptType
import com.waz.model.{MessageId, ReadReceipt, UserId}
import com.waz.service.conversation.ConversationsService
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future
import scala.concurrent.Future.successful

class ReceiptService(messages: MessagesStorage,
                     sync: SyncServiceHandle,
                     selfUserId: UserId,
                     convsService: ConversationsService,
                     readReceiptsStorage: ReadReceiptsStorage) extends DerivedLogTag {
  import EventContext.Implicits.global
  import Threading.Implicits.Background

  messages.onAdded { msgs =>
    val filteredMessages = msgs.filter(msg => msg.userId != selfUserId && confirmable(msg.msgType)).groupBy(m => (m.convId, m.userId))
    Future.traverse(filteredMessages) { case ((convId, userId), groupMessages) =>
      for {
        false <- convsService.isGroupConversation(convId)
        _     <- sync.postReceipt(convId, groupMessages.map(_.id), userId, ReceiptType.Delivery)
      } yield ()
    }
  }

  val confirmable = Set(TEXT, TEXT_EMOJI_ONLY, IMAGE_ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, KNOCK,
    RICH_MEDIA, HISTORY_LOST, LOCATION, COMPOSITE)

  def processDeliveryReceipts(receipts: Seq[MessageId]) =
    if (receipts.nonEmpty) {
      debug(l"received receipts: $receipts")
      messages.updateAll2(receipts, _.copy(state = DELIVERED))
    } else successful(Seq.empty)

  def processReadReceipts(receipts: Seq[ReadReceipt]): Future[Set[ReadReceipt]] =
    if (receipts.nonEmpty) {
      debug(l"received read receipts: $receipts")
      readReceiptsStorage.insertAll(receipts)
    } else successful(Set.empty)

}
