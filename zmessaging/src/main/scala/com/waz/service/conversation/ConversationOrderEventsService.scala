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
package com.waz.service.conversation

import com.waz.log.LogSE.{verbose, _}
import com.waz.content.{ConversationStorage, MessagesStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent._
import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.service.messages.MessagesService
import com.waz.service.{EventPipeline, EventScheduler, UserService}
import com.waz.sync.SyncServiceHandle
import com.waz.utils._

import java.util.UUID
import scala.concurrent.Future

class ConversationOrderEventsService(selfUserId: UserId,
                                     convs:      ConversationsContentUpdater,
                                     storage:    ConversationStorage,
                                     messages:   MessagesService,
                                     msgStorage: MessagesStorage,
                                     users:      UserService,
                                     sync:       SyncServiceHandle,
                                     pipeline:   EventPipeline) extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private[service] def shouldChangeOrder(event: ConversationEvent): Boolean =
    event match {
      case _: CreateConversationEvent => true
      case _: DeleteConversationEvent => true
      case _: CallMessageEvent        => true
      case _: OtrErrorEvent           => true
      case _: ConnectRequestEvent     => true
      case _: OtrMessageEvent         => true
      case MemberJoinEvent(_, _, _, _, _, added, _, _) if added.contains(selfUserId) => true
      case MemberLeaveEvent(_, _, _, _, _, leaving, _) if leaving.contains(selfUserId) => true
      case GenericMessageEvent(_, _, _, _, _, gm: GenericMessage) =>
        gm.unpackContent match {
          case _: Asset               => true
          case _: Calling             => true
          case _: Cleared             => false
          case _: ClientAction        => false
          case _: DeliveryReceipt     => false
          case _: Ephemeral           => true
          case _: AvailabilityStatus  => false
          case _: External            => true
          case _: ImageAsset          => true
          case _: Knock               => true
          case _: LastRead            => false
          case _: Location            => true
          case _: MsgRecall           => false
          case _: MsgEdit             => false
          case _: MsgDeleted          => false
          case _: Reaction            => false
          case _: Text                => true
          case _: DataTransfer        => false
          case _                      => false
        }
      case _ => false
    }

  private[service] def shouldUnarchive(event: ConversationEvent): Boolean =
    event match {
      case MemberLeaveEvent(_, _, _, _, _, leaving, _) if leaving contains selfUserId => false
      case _ => shouldChangeOrder(event)
    }

  val conversationOrderEventsStage: Stage.Atomic = EventScheduler.Stage[ConversationEvent] ({ (convId, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService stage 1")
    val orderChanges    = processConversationOrderEvents(convId, events.filter(shouldChangeOrder), tag)
    verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService stage 2")
    val unarchiveConvs  = processConversationUnarchiveEvents(convId, events.filter(shouldUnarchive), tag)
    verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService stage 3")

    for {
      _ <- orderChanges
      _ <- unarchiveConvs
    } yield {
      verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService stage 4")
      Unit
    }
  },
    name = "ConversationOrderEventsService - ConversationEvent"
  )

  def handlePostConversationEvent(event: ConversationEvent): Future[Unit] =
    Future.sequence(Seq(
      event match {
        case ev: MessageEvent => pipeline(Seq(ev.withCurrentLocalTime()), None) // local time is required for the hot knock mechanism
        case _ => Future.successful(())
      },

      convs.convByRemoteId(event.convId) flatMap {
        case Some(conv) =>
          convs.updateConversationLastRead(conv.id, event.time) map { _ => Future.successful(()) }
        case _ => Future.successful(())
      }
    )) map { _ => () }

  private def processConversationOrderEvents(convId: RConvId, es: Seq[ConversationEvent], tag: Option[UUID] = None) = {
    verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationOrderEvents 1")

    if (es.isEmpty) {
      verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationOrderEvents empty")
      Future.successful(())
    }
    else convs.processConvWithRemoteId(None, convId, retryAsync = true) { conv =>
      verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConvWithRemoteId")
      verbose(l"processConversationOrderEvents($conv, $es)")
      val lastTime = es.maxBy(_.time).time
      val fromSelf = es.filter(_.from == selfUserId)
      val lastRead = if (fromSelf.isEmpty) None else Some(fromSelf.maxBy(_.time).time)

      verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService lastRead = $lastRead")

      for {
        _ <- convs.updateLastEvent(conv.id, lastTime, tag)
        _ <- lastRead match {
          case None => Future successful None
          case Some(time) => convs.updateConversationLastRead(conv.id, time, tag)
        }
      } yield ()
    }
  }

  private def processConversationUnarchiveEvents(convId: RConvId, events: Seq[ConversationEvent], tag: Option[UUID] = None) = {
    verbose(l"processConversationUnarchiveEvents($convId, ${events.size} events)")
    verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationUnarchiveEvents 1")
    for {
      convs   <- Future.sequence(events.filter(shouldUnarchive).groupBy(_.convId).map {
                  case (rId, es) if hasMentions(es) =>
                    Future.successful(rId -> (es.maxBy(_.time).time, unarchiveMuted(es), true))
                  case (rId, es) =>
                    hasSelfQuotes(es).map(hasQuotes => rId -> (es.maxBy(_.time).time, unarchiveMuted(es), hasQuotes))
                 }).map(_.toMap).map( { f =>
          verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationUnarchiveEvents 2")
          f
      })
      convIds <- storage.getByRemoteIds(convs.keys).map({ f =>
        verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationUnarchiveEvents 3")
        f
      })
      updates <- storage.updateAll2(convIds, { conv =>
                   convs.get(conv.remoteId) match {
                     case Some((time, unarchiveMuted, hasMentionOrQuote)) if conv.archiveTime.isBefore(time) && (conv.isAllAllowed || unarchiveMuted || (conv.onlyMentionsAllowed && hasMentionOrQuote)) =>
                       conv.copy(archived = false, archiveTime = time)
                     case _ =>
                       conv
                   }
                 }).map({ f =>
        verbose(l"SSSTAGES<TAG:$tag> ConversationOrderEventsService processConversationUnarchiveEvents 4")
        f
      })
    } yield updates
  }

  private def unarchiveMuted(events: Seq[ConversationEvent]): Boolean =
    events.exists {
      case GenericMessageEvent(_, _, _, _, _, gm: GenericMessage) =>
        gm.unpackContent match {
          case _: Knock => true
          case _ => false
        }
      case _ => false
    }

  private def hasMentions(events: Seq[ConversationEvent]): Boolean = events.exists {
    case GenericMessageEvent(_, _, _, _, _, gm: GenericMessage) =>
      gm.unpackContent match {
        case text: Text =>
          val (_, mentions, _, _, _) = text.unpack
          mentions.exists(_.userId.contains(selfUserId))
        case _ => false
      }
    case _ => false
  }

  private def hasSelfQuotes(events: Seq[ConversationEvent]): Future[Boolean] = {
    object OriginalId {
      def unapply(event: GenericMessageEvent): Option[MessageId] = event.content.unpackContent match {
        case text: Text =>
          val (_, _, _, quote, _) = text.unpack
          quote.map(_.unpack._1)
        case _ => None
      }
    }

    val originalIds = events.collect { case OriginalId(originalId) => originalId }
    msgStorage.getMessages(originalIds: _*).map(
      _.exists(_.exists(_.userId == selfUserId))
    )
  }
}
