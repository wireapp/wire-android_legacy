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
package com.waz.service

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.{ReadReceipt => GReadReceipt}
import com.waz.model.{ReadReceipt => MReadReceipt}
import com.waz.model.GenericContent._
import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdaterImpl}
import com.waz.service.messages.{MessagesContentUpdater, ReactionsService, ReceiptService}
import com.waz.service.tracking.TrackingService

import scala.concurrent.Future
import scala.concurrent.Future.traverse

class GenericMessageService(selfUserId: UserId,
                            messages:   MessagesContentUpdater,
                            convs:      ConversationsContentUpdaterImpl,
                            convEvents: ConversationOrderEventsService,
                            reactions:  ReactionsService,
                            receipts:   ReceiptService,
                            users:      UserService,
                            tracking:   TrackingService
                           ) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  // performance optimization - private mutable caches
  // thanks to them we don't need to allocate memory for sequences with every new event
  import scala.collection.mutable
  private val incomingReactions   = new mutable.ArrayBuffer[Liking]()
  private val lastRead            = new mutable.ArrayBuffer[(RConvId, RemoteInstant)]()
  private val cleared             = new mutable.ArrayBuffer[(RConvId, RemoteInstant)]()
  private val deleted             = new mutable.ArrayBuffer[MessageId]()
  private val confirmed           = new mutable.ArrayBuffer[MessageId]()
  private val availabilities      = new mutable.HashMap[UserId, Availability]()
  private val read                = new mutable.ArrayBuffer[MReadReceipt]()
  private val buttonConfirmations = new mutable.HashMap[MessageId, Option[ButtonId]]()
  private val newTrackingIds      = new mutable.ArrayBuffer[TrackingId]()

  private def clearCaches(): Unit = {
    incomingReactions.clear()
    lastRead.clear()
    cleared.clear()
    deleted.clear()
    confirmed.clear()
    availabilities.clear()
    read.clear()
    buttonConfirmations.clear()
    newTrackingIds.clear()
  }

  private def updateCaches(events: Seq[GenericMessageEvent]): Unit = {
    clearCaches()
    events.foreach { case GenericMessageEvent(_, _, time, from, _, content) =>
      content.unpackContent match {
        case r: Reaction =>
          val (msg, action) = r.unpack
          incomingReactions += Liking(msg, from, time, action)
        case lr: LastRead =>
          lastRead += lr.unpack
        case c: Cleared if from == selfUserId =>
          cleared += c.unpack
        case msg: MsgDeleted =>
          val (_, msgId) = msg.unpack
          deleted += msgId
        case dr: DeliveryReceipt =>
          confirmed ++= dr.unpack.getOrElse(Seq.empty)
        case av: AvailabilityStatus =>
          av.unpack.foreach(availabilities += from -> _)
        case r: GReadReceipt =>
          read ++= r.unpack.getOrElse(Seq.empty).map(msg => MReadReceipt(msg, from, time))
        case bac: ButtonActionConfirmation =>
          val (msgId, buttonId) = bac.unpack
          buttonConfirmations += msgId -> buttonId
        case dt: DataTransfer if from == selfUserId =>
          newTrackingIds += dt.unpack
        case _ =>
      }
    }
  }

  private def process(events: Seq[GenericMessageEvent]) =
    for {
      _ <- Future.successful { updateCaches(events) }
      _ <- messages.deleteOnUserRequest(deleted)
      _ <- traverse(lastRead) { case (remoteId, timestamp) =>
        convs.processConvWithRemoteId(None, remoteId, retryAsync = true) { conv => convs.updateConversationLastRead(conv.id, timestamp) }
      }
      _ <- reactions.processReactions(incomingReactions)
      _ <- traverse(cleared) { case (remoteId, timestamp) =>
        convs.processConvWithRemoteId(None, remoteId, retryAsync = true) { conv => convs.updateConversationCleared(conv.id, timestamp) }
      }
      _ <- receipts.processDeliveryReceipts(confirmed)
      _ <- receipts.processReadReceipts(read)
      _ <- users.storeAvailabilities(availabilities.toMap)
      _ <- messages.updateButtonConfirmations(buttonConfirmations.toMap)
      _ =  newTrackingIds.lastOption.foreach { tracking.onTrackingIdChange ! _ }
    } yield ()

  private var processing = Future.successful(())

  val eventProcessingStage: Stage = EventScheduler.Stage[GenericMessageEvent] ({ (_, events, tag) => {
    verbose(l"SSSTAGES<TAG:$tag> GenericMessageService stage 1")
    synchronized {
      verbose(l"SSSTAGES<TAG:$tag> GenericMessageService stage 2")
      processing = if (processing.isCompleted) process(events) else processing.flatMap(_ => process(events))
      verbose(l"SSSTAGES<TAG:$tag> GenericMessageService stage 3")
      processing
      }
    }
  },
    name = "GenericMessageService - GenericMessageEvent"
  )
}
