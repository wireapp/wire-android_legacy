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

import com.waz.ZLog
import com.waz.content.ConversationStorage
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, MemberJoinEvent, _}
import com.waz.service.EventScheduler.{Interleaved, Parallel, Sequential, Stage}
import com.waz.service.messages.MessagesService
import com.waz.service.{EventPipelineImpl, EventScheduler, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.threading.SerialDispatchQueue
import org.threeten.bp.Instant

import scala.concurrent.{ExecutionContext, Future}

class ConversationOrderEventsServiceSpec extends AndroidFreeSpec {

  implicit val outputDispatcher = new SerialDispatchQueue(name = "OutputWriter")

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
      RenameConversationEvent(convId, RemoteInstant(clock.instant()), UserId(), "blah")
    }

    result(pipeline.apply(events).map(_ => println(output.toString)))
  }

  lazy val convs     = mock[ConversationsContentUpdater]
  lazy val storage   = mock[ConversationStorage]
  lazy val messages  = mock[MessagesService]
  lazy val users     = mock[UserService]
  lazy val sync      = mock[SyncServiceHandle]

  scenario("System messages shouldn't change order except it contains self") {

    val selfUserId = UserId("user1")
    val convId = ConvId()
    val rConvId = RConvId()
    val conv = ConversationData(ConvId(), rConvId, Some("name"), UserId(), ConversationType.Group, lastEventTime = RemoteInstant.Epoch)
    var updatedConv = conv.copy()

    (storage.getByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq(convId)))

    (convs.processConvWithRemoteId[Unit] (_: RConvId, _: Boolean, _: Int)(_: ConversationData => Future[Unit])(_:ZLog.LogTag, _:ExecutionContext)).expects(*, *, *, *, *, *).onCall {
      p: Product =>
        p.productElement(3).asInstanceOf[ConversationData => Future[Unit]].apply(conv)
    }

    (convs.updateLastEvent _).expects(*, *).once().onCall { (_: ConvId, i: RemoteInstant) =>
      updatedConv = conv.copy(lastEventTime = i)
      Future.successful(Some(conv, updatedConv))
    }

    lazy val scheduler: EventScheduler = new EventScheduler(Stage(Sequential)(service.conversationOrderEventsStage))
    lazy val pipeline  = new EventPipelineImpl(Vector.empty, scheduler.enqueue)
    lazy val service = new ConversationOrderEventsService(selfUserId, convs, storage, messages, users, sync, pipeline)

    val events = Seq(
      MemberJoinEvent(rConvId, RemoteInstant.ofEpochMilli(1), UserId(), Seq(selfUserId)),
      RenameConversationEvent(rConvId, RemoteInstant.ofEpochMilli(2), UserId(), "blah"),
      MemberJoinEvent(rConvId, RemoteInstant.ofEpochMilli(3), UserId(), Seq(UserId("user2"))),
      MemberLeaveEvent(rConvId, RemoteInstant.ofEpochMilli(4), UserId(), Seq(UserId("user3"))))

    result(pipeline.apply(events))
    updatedConv.lastEventTime shouldBe RemoteInstant.ofEpochMilli(1)
  }

}
