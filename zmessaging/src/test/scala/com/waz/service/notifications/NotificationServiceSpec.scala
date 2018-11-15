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
package com.waz.service.notifications

import com.waz.api.Message
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.content.{ConversationStorage, MessagesStorage, NotificationStorage}
import com.waz.model.GenericContent.{MsgDeleted, MsgEdit, MsgRecall, Reaction, Text}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.NotificationsHandler.NotificationType.LIKE
import com.waz.model.ConversationData.ConversationType
import com.waz.service.push.{NotificationService, NotificationUiController, PushService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils.events.Signal
import com.waz.utils._
import org.threeten.bp.Duration

import scala.collection.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationServiceSpec extends AndroidFreeSpec {

  val messages      = mock[MessagesStorage]
  val storage       = mock[NotificationStorage]
  val convs         = mock[ConversationStorage]
  val pushService   = mock[PushService]
  val uiController  = mock[NotificationUiController]

  val beDrift = Signal(Duration.ZERO)
  val uiNotificationsSourceVisible = Signal(Map.empty[UserId, Set[ConvId]])

  val notificationsDispatcher = new SerialDispatchQueue()
  val storedNotifications = Signal(Set.empty[NotificationData])

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    storedNotifications ! Set.empty

    //advance the clock to avoid weird problems around the epoch (e.g.)
    clock + 24.hours
  }

  feature("Message events") {
    scenario("Process basic text message notifications") {
      val rConvId = RConvId("conv")
      val conv = ConversationData(remoteId = rConvId)

      val lastEventTime = RemoteInstant.apply(clock.instant())

      val content = TextMessage("abc")
      val from = UserId("User1")
      val event = GenericMessageEvent(rConvId, lastEventTime, from, content)

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))
      (messages.getAll _).expects(*).twice().returning(Future.successful(Seq.empty))
      (messages.findMessagesFrom _).expects(conv.id, lastEventTime).returning(Future.successful(
        IndexedSeq(
          MessageData(
            MessageId(content.messageId),
            conv.id,
            msgType = Message.Type.TEXT,
            protos = Seq(content),
            userId = from,
            time   = lastEventTime
          )
        )
      ))
      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 1
        nots.head.msg shouldEqual "abc"
        nots.head.hasBeenDisplayed shouldEqual false
        Future.successful({})
      }

      val service = getService()

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))
    }

    scenario("Previous notifications that have not been dismissed are passed with notifications from new events") {

      val rConvId = RConvId("conv")
      val conv = ConversationData(remoteId = rConvId)

      //prefil notification storage
      val previousNots = Set(
        NotificationData(hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant.apply(clock.instant)),
        NotificationData(hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant.apply(clock.instant))
      )
      storedNotifications ! previousNots


      val lastEventTime = RemoteInstant.apply(clock.instant())

      val content = TextMessage("abc")
      val from = UserId("User1")
      val event = GenericMessageEvent(rConvId, lastEventTime, from, content)

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))
      (messages.getAll _).expects(*).twice().returning(Future.successful(Seq.empty))
      (messages.findMessagesFrom _).expects(conv.id, lastEventTime).returning(Future.successful(
        IndexedSeq(
          MessageData(
            MessageId(content.messageId),
            conv.id,
            msgType = Message.Type.TEXT,
            protos = Seq(content),
            userId = from,
            time   = lastEventTime
          )
        )
      ))
      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 3
        nots.exists(_.msg == "abc") shouldEqual true
        val (shown, toShow) = nots.partition(_.hasBeenDisplayed)
        shown.size shouldEqual 2
        toShow.size shouldEqual 1
        Future.successful({})
      }

      val service = getService()

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))
    }

    scenario("Apply multiple message edit events to previous notifications") {

      val rConvId = RConvId("conv")
      val conv = ConversationData(remoteId = rConvId)

      val from = UserId("User1")

      val origEventTime = RemoteInstant.apply(clock.instant())
      val edit1EventTime = RemoteInstant.apply(clock.instant() + 10.seconds)
      val edit2EventTime = RemoteInstant.apply(clock.instant() + 10.seconds)

      val originalContent = GenericMessage(Uid("messageId"), Text("abc"))

      val editContent1 = GenericMessage(Uid("edit-id-1"), MsgEdit(MessageId(originalContent.messageId), Text("def")))
      val editEvent1 = GenericMessageEvent(rConvId, edit1EventTime, from, editContent1)

      val editContent2 = GenericMessage(Uid("edit-id-2"), MsgEdit(MessageId(editContent1.messageId), Text("ghi")))
      val editEvent2 = GenericMessageEvent(rConvId, edit2EventTime, from, editContent2)

      val originalNotification = NotificationData(
        id = NotId(originalContent.messageId),
        msg = "abc",
        conv = conv.id,
        user = from,
        msgType = NotificationType.TEXT,
        time = origEventTime,
        hasBeenDisplayed = true
      )

      storedNotifications ! Set(originalNotification)

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))
      (messages.getAll _).expects(*).twice().returning(Future.successful(Seq.empty))
      (messages.findMessagesFrom _).expects(conv.id, edit1EventTime).returning(Future.successful(IndexedSeq.empty))
      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        val not = nots.head
        not.id shouldEqual NotId(editContent2.messageId)
        not.msg shouldEqual "ghi"
        Future.successful({})
      }

      val service = getService()

      result(service.messageNotificationEventsStage(rConvId, Vector(editEvent1, editEvent2)))
    }

    scenario("Apply delete and recall (hide and delete) events to previous notifications in storage and stream") {

      val rConvId = RConvId("conv")
      val conv = ConversationData(remoteId = rConvId)

      //prefil notification storage
      val toBeDeletedNotif = NotificationData(NotId("not-id-1"), hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant(clock.instant))
      val remainingNotif = NotificationData(NotId("not-id-2"), hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant(clock.instant))

      val previousNots = Set(
        toBeDeletedNotif,
        remainingNotif
      )
      storedNotifications ! previousNots

      val from = UserId("User1")
      val msgContent = GenericMessage(Uid("messageId"), Text("abc"))
      val msgEvent = GenericMessageEvent(rConvId, RemoteInstant(clock.instant()), from, msgContent)

      val deleteContent1 = GenericMessage(Uid(), MsgDeleted(rConvId, MessageId(toBeDeletedNotif.id.str)))
      val deleteEvent1 = GenericMessageEvent(rConvId, RemoteInstant.apply(clock.instant()), from, deleteContent1)

      val deleteContent2 = GenericMessage(Uid(), MsgRecall(MessageId(msgContent.messageId)))
      val deleteEvent2 = GenericMessageEvent(rConvId, RemoteInstant.apply(clock.instant()), from, deleteContent2)

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))

      (messages.getAll _).expects(*).twice().returning(Future.successful(Seq.empty))

      (messages.findMessagesFrom _).expects(conv.id, *).returning(Future.successful(IndexedSeq.empty))

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        Future.successful {
          nots.size shouldEqual 1
          nots.head shouldEqual remainingNotif
        }
      }

      val service = getService()
      result(service.messageNotificationEventsStage(rConvId, Vector(msgEvent, deleteEvent1, deleteEvent2)))
    }

    scenario("Multiple alternative likes and unlikes only ever apply the last event") {

      val rConvId = RConvId("r-conv")
      val conv = ConversationData(id = ConvId("conv"), remoteId = rConvId)

      val from = UserId("User1")
      val from2 = UserId("User2")

      val messageTime = RemoteInstant.apply(clock.instant() + 5.seconds)
      val like1EventTime =  RemoteInstant.apply(clock.instant() + 10.seconds)
      val unlikeEventTime = RemoteInstant.apply(clock.instant() + 20.seconds)
      val otherEventTime =  RemoteInstant.apply(clock.instant() + 20.seconds) //a like from a different user
      val like2EventTime =  RemoteInstant.apply(clock.instant() + 30.seconds)

      val likedMessageId = MessageId("message")

      val like1Content = GenericMessage(Uid("like1-id"), Reaction(likedMessageId, Liking.Action.Like))
      val like1Event = GenericMessageEvent(rConvId, like1EventTime, from, like1Content)

      val unlikeContent = GenericMessage(Uid("unlike-id"), Reaction(likedMessageId, Liking.Action.Unlike))
      val unlikeEvent = GenericMessageEvent(rConvId, unlikeEventTime, from, unlikeContent)

      val like2Content = GenericMessage(Uid("like2-id"), Reaction(likedMessageId, Liking.Action.Like))
      val like2Event = GenericMessageEvent(rConvId, like2EventTime, from, like2Content)

      val otherLikeContent = GenericMessage(Uid("like3-id"), Reaction(likedMessageId, Liking.Action.Like))
      val otherLikeEvent = GenericMessageEvent(rConvId, otherEventTime, from2, otherLikeContent)

      val originalMessage =
        MessageData(
          likedMessageId,
          conv.id,
          msgType = Message.Type.TEXT,
          userId = account1Id,
          time   = messageTime
        )

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))
      (messages.getAll _).expects(Set(likedMessageId)).once().returning(Future.successful(Seq(Some(originalMessage))))
      (messages.getAll _).expects(*).once().returning(Future.successful(Seq.empty))
      (messages.findMessagesFrom _).expects(conv.id, like1EventTime).returning(Future.successful(IndexedSeq.empty))

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 2
        nots.foreach { n =>
          n.msgType shouldEqual NotificationType.LIKE
        }
        nots.map(_.id).contains(NotId(s"$LIKE-${likedMessageId.str}-${from.str}")) shouldEqual true
        nots.map(_.id).contains(NotId(s"$LIKE-${likedMessageId.str}-${from2.str}")) shouldEqual true
        Future.successful({})
      }

      val service = getService()

      result(service.messageNotificationEventsStage(rConvId, Vector(like1Event, unlikeEvent, otherLikeEvent, like2Event)))
    }
  }

  feature ("Conversation state events") {

    scenario("Group creation events") {
      val rConvId = RConvId("conv")
      val conv = ConversationData(remoteId = rConvId)

      val lastEventTime = RemoteInstant.apply(clock.instant())

      val from = UserId("User1")
      val generatedMessageId = MessageId()
      val event = CreateConversationEvent(rConvId, RemoteInstant(clock.instant()), from, ConversationResponse(
        rConvId, Some(Name("conv")), from, ConversationType.Group, None, MuteSet.AllAllowed, RemoteInstant.Epoch, archived = false, RemoteInstant.Epoch, Set.empty, None, None, None, Set(account1Id, from), None
      ))

      (convs.getByRemoteId _).expects(rConvId).once().returning(Future.successful(Some(conv)))
      (convs.getAll _).expects(Set(conv.id)).once().returning(Future.successful(Seq(Some(conv))))
      (messages.getAll _).expects(*).twice().returning(Future.successful(Seq.empty))
      (messages.findMessagesFrom _).expects(conv.id, lastEventTime).returning(Future.successful(
        IndexedSeq(
          MessageData(
            generatedMessageId,
            conv.id,
            msgType = Message.Type.MEMBER_JOIN,
            userId = from,
            time   = lastEventTime
          )
        )
      ))
      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 1
        nots.head.msg shouldEqual ""
        nots.head.msgType shouldEqual NotificationType.MEMBER_JOIN
        nots.head.hasBeenDisplayed shouldEqual false
        Future.successful({})
      }

      val service = getService()

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))
    }

  }

  feature("Dismissing notifications when their conversation is seen") {

    scenario("Seeing conversation removes all notifications for that conversation and updates UI") {

      val conv1 = ConversationData()
      val conv2 = ConversationData()

      //prefil notification storage
      val previousNots = Set(
        NotificationData(hasBeenDisplayed = true, conv = conv1.id, time = RemoteInstant.apply(clock.instant)),
        NotificationData(hasBeenDisplayed = true, conv = conv1.id, time = RemoteInstant.apply(clock.instant)),
        NotificationData(hasBeenDisplayed = true, conv = conv2.id, time = RemoteInstant.apply(clock.instant)) //a different conv!
      )
      storedNotifications ! previousNots

      (convs.getAll _).expects(*).once().returning(Future.successful(Seq(Some(conv1), Some(conv2))))

      val uiNotified = Signal(false)
      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        Future {
          nots.size shouldEqual 1
          nots.head.conv shouldEqual conv2.id
          uiNotified ! true
        } (Threading.Background)
      }

      val service = getService()

      uiNotificationsSourceVisible ! Map(account1Id -> Set(conv1.id))
      result(uiNotified.filter(identity(_)).head)
    }

  }

  def getService() = {

    (pushService.beDrift _).expects().anyNumberOfTimes().returning(beDrift)
    (uiController.notificationsSourceVisible _).expects().anyNumberOfTimes().returning(uiNotificationsSourceVisible)

    implicit val ec = notificationsDispatcher
    (storage.insertAll _).expects(*).anyNumberOfTimes().onCall { (toAdd: Traversable[NotificationData]) =>
      notificationsDispatcher {
        storedNotifications.mutate { nots =>
          nots ++ toAdd
        }
        Set.empty[NotificationData] //return not important
      }.future
    }

    (storage.removeAll _).expects(*).anyNumberOfTimes().onCall { (toRemove: Iterable[NotId]) =>
      notificationsDispatcher {
        storedNotifications.mutate { nots =>
          nots.filterNot(n => toRemove.toSet.contains(n.id))
        }
      }.future.map(_ => {})
    }

    (storage.list _).expects().anyNumberOfTimes().onCall { _ =>
      storedNotifications.head.map(_.toSeq)
    }

    new NotificationService(account1Id, messages, storage, convs, pushService, uiController, clock)
  }

}
