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

import com.waz.content.{ConversationStorage, MessagesStorage}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, GenericMessage, GenericMessageEvent, _}
import com.waz.service.EventScheduler.{Interleaved, Parallel, Sequential, Stage}
import com.waz.service.messages.MessagesService
import com.waz.service.{EventPipeline, EventPipelineImpl, EventScheduler, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.wire.signals.SerialDispatchQueue
import com.wire.signals.Signal
import org.threeten.bp.Instant
import com.waz.model.GenericContent.Quote
import com.waz.model.Messages.LegalHoldStatus
import com.waz.zms.BuildConfig

import scala.concurrent.{ExecutionContext, Future}

class ConversationOrderEventsServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  implicit val outputDispatcher = SerialDispatchQueue(name = "OutputWriter")

  private val domain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty

  scenario("All batched conversation events go to the order event service before any other conv-related service") {

    val output = new StringBuffer()

    var convOrderProcessedCount = 0
    val convOrderStage = EventScheduler.Stage[ConversationEvent] { (convId, es) =>
      Future {
        output.append(s"A$convOrderProcessedCount")
        convOrderProcessedCount += 1
      }
    }

    var convStateProcessedCount = 0
    val convStateStage = EventScheduler.Stage[ConversationStateEvent] { (convId, es) =>
      Future {
        output.append(s"B$convStateProcessedCount")
        convStateProcessedCount += 1
      }
    }

    val messagesEventStage = EventScheduler.Stage[MessageEvent] { (convId, events) => Future.successful({}) }

    val genericMessageEventStage = EventScheduler.Stage[GenericMessageEvent] { (convId, events) => Future.successful({}) }


    val scheduler = new EventScheduler(
      Stage(Sequential)(
        convOrderStage,
        Stage(Parallel)(
          convStateStage,
          Stage(Interleaved)(
            messagesEventStage,
            genericMessageEventStage
          )
        )
      )
    )

    val pipeline = new EventPipelineImpl(Vector.empty, scheduler.enqueue)

    val convId = RConvId()
    val events = (1 to 10).map { _ =>
      RenameConversationEvent(convId, domain, RemoteInstant(clock.instant()), UserId(), domain, Name("blah"))
    }

    result(pipeline.apply(events).map(_ => println(output.toString)))
  }

  lazy val convs      = mock[ConversationsContentUpdater]
  lazy val storage    = mock[ConversationStorage]
  lazy val messages   = mock[MessagesService]
  lazy val msgStorage = mock[MessagesStorage]
  lazy val users      = mock[UserService]
  lazy val sync       = mock[SyncServiceHandle]

  val selfUserId = UserId("user1")
  val convId = ConvId("conv_id1")
  val rConvId = RConvId("r_conv_id1")
  val convsInStorage = Signal[Map[ConvId, ConversationData]]()

  (storage.getByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq(convId)))
  (convs.processConvWithRemoteId[Unit] (_: RConvId, _: Boolean, _: Int)(_: ConversationData => Future[Unit])(_:LogTag, _:ExecutionContext)).expects(*, *, *, *, *, *).anyNumberOfTimes.onCall {
    p: Product =>
      convsInStorage.head.map(_.head._2).flatMap { conv =>
        p.productElement(3).asInstanceOf[ConversationData => Future[Unit]].apply(conv)
      }
  }

  (convs.updateLastEvent _).expects(*, *).anyNumberOfTimes.onCall { (convId : ConvId, i: RemoteInstant) =>
    Future.successful {
      var prev: ConversationData = null
      var updated: ConversationData = null
      convsInStorage.mutate { convs =>
        prev = convs(convId)
        updated = convs(convId).copy(lastEventTime = i)
        convsInStorage.mutate(_ + (convId -> updated))
        convs + (convId -> updated)
      }
      Some(prev, updated)
    }
  }

  (storage.updateAll2 _).expects(*, *).anyNumberOfTimes.onCall { (keys: Iterable[ConvId], updater: ConversationData => ConversationData) =>
    Future.successful {
      val ids = keys.toSet
      var updates: Map[ConvId, (ConversationData, ConversationData)] = null
      convsInStorage.mutate { convs =>
        updates = convs.filterKeys(ids.contains).map { case (id, c) => id -> (c, updater(c)) }
        convs.map {
          case (id, c) if updates.contains(id) => id -> updates(id)._2
          case (id, c) => id -> c
        }
      }
      updates.values.toSeq
    }
  }

  (storage.get _).expects(*).anyNumberOfTimes.onCall { id: ConvId => convsInStorage.head.map(_.get(id)) }

  lazy val pipeline = mock[EventPipeline]
  (pipeline.apply _).expects(*).anyNumberOfTimes().returning(Future.successful({}))
  lazy val service = new ConversationOrderEventsService(selfUserId, convs, storage, messages, msgStorage, users, sync, pipeline)

/*
  scenario("System messages shouldn't change order except it contains self") {

    val conv = ConversationData(convId, rConvId, Some(Name("name")), UserId("creator"), ConversationType.Group, lastEventTime = RemoteInstant.Epoch)
    convsInStorage ! Map(convId -> conv)

    (msgStorage.getMessages _).expects(Vector.empty).once().returns(Future.successful(Seq.empty))

    val events = Seq(
      MemberJoinEvent(rConvId, RemoteInstant.ofEpochMilli(1), UserId("user_who_added_1"), Seq(selfUserId)),
      RenameConversationEvent(rConvId, RemoteInstant.ofEpochMilli(2), UserId("user_who_added_2"), Name("blah")),
      MemberJoinEvent(rConvId, RemoteInstant.ofEpochMilli(3), UserId("user_who_added_3"), Seq(UserId("user2"))),
      MemberLeaveEvent(rConvId, RemoteInstant.ofEpochMilli(4), UserId("user_who_added_4"), Seq(UserId("user3"))))

    result(service.conversationOrderEventsStage.apply(rConvId, events))
    result(storage.get(convId)).map(_.lastEventTime) shouldBe Some(RemoteInstant.ofEpochMilli(1))
  }
*/

  scenario("Unarchive conversation with a new message if not muted") {

    val conv = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = true,
      muted = MuteSet.AllAllowed
    )

    convsInStorage ! Map(convId -> conv)

    (msgStorage.getMessages _).expects(Vector.empty).once.returns(Future.successful(Seq.empty))

    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    val events = Seq(
      GenericMessageEvent(rConvId,  domain, RemoteInstant.ofEpochMilli(1), UserId(), domain, GenericMessage.TextMessage("hello", Nil, expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )

    result(service.conversationOrderEventsStage.apply(rConvId, events))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(false)

  }

  scenario("Unarchive conversation with a mention") {
    val conv = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = true,
      muted = MuteSet.OnlyMentionsAllowed
    )

    convsInStorage ! Map(convId -> conv)

    (msgStorage.getMessages _).expects(Vector.empty).once().returns(Future.successful(Seq.empty))

    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    // false positive check: don't unarchive because of a standard text message
    val events1 = Seq(
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(1), UserId(), domain, GenericMessage.TextMessage("hello", Nil, expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )
    result(service.conversationOrderEventsStage.apply(rConvId, events1))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    val events2 = Seq(
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(2), UserId(), domain, GenericMessage.TextMessage("hello @user", Seq(Mention(Some(selfUserId), 6, 11)), expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )
    result(service.conversationOrderEventsStage.apply(rConvId, events2))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(false)
  }

  scenario("Unarchive conversation with a reply") {
    val conv = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = true,
      muted = MuteSet.OnlyMentionsAllowed
    )

    convsInStorage ! Map(convId -> conv)

    val msgId = MessageId()
    val message = MessageData(msgId, convId, userId = selfUserId)

    (msgStorage.getMessages _).expects(*).twice().onCall { ids: Seq[MessageId] =>
      val msgs = ids.map(id => if (id == msgId) Some(message) else None)
      Future.successful(msgs)
    }

    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    // false positive check: don't unarchive because of a standard text message
    val events1 = Seq(
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(1), UserId(),domain, GenericMessage.TextMessage("hello", Nil, expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )
    result(service.conversationOrderEventsStage.apply(rConvId, events1))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    val events2 = Seq(
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(2), UserId(), domain, GenericMessage.TextMessage("hello @user", Nil, Nil, Some(Quote(msgId, None)), expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )
    result(service.conversationOrderEventsStage.apply(rConvId, events2))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(false)
  }

  scenario("Do not unarchive conversation") {
    val conv = ConversationData(
      convId,
      rConvId,
      Some(Name("name")),
      UserId(),
      ConversationType.Group,
      lastEventTime = RemoteInstant.Epoch,
      archived = true,
      muted = MuteSet.AllMuted
    )

    convsInStorage ! Map(convId -> conv)

    val msgId = MessageId()
    val message = MessageData(msgId, convId, userId = selfUserId)

    (msgStorage.getMessages _).expects(*).anyNumberOfTimes.onCall { ids: Seq[MessageId] =>
      val msgs = ids.map(id => if (id == msgId) Some(message) else None)
      Future.successful(msgs)
    }

    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)

    val events = Seq(
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(1), UserId(), domain, GenericMessage.TextMessage("hello", Nil, expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN)),
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(2), UserId(), domain, GenericMessage.TextMessage("hello @user", Seq(Mention(Some(selfUserId), 6, 11)), expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN)),
      GenericMessageEvent(rConvId, domain, RemoteInstant.ofEpochMilli(3), UserId(), domain, GenericMessage.TextMessage("hello @user", Nil, Nil, Some(Quote(msgId, None)), expectsReadConfirmation = false, LegalHoldStatus.UNKNOWN))
    )
    result(pipeline.apply(events))
    result(storage.get(convId)).map(_.archived) shouldEqual Some(true)
  }
}
