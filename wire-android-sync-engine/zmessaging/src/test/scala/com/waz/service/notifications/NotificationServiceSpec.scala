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
import com.waz.api.NotificationsHandler.NotificationType.LIKE
import com.waz.content.{ConversationStorage, MessagesStorage, NotificationStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{MsgDeleted, MsgEdit, MsgRecall, Reaction, Text}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.service.UserService
import com.waz.service.push.{NotificationServiceImpl, NotificationUiController, PushService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import org.threeten.bp.Duration

import scala.collection.Seq
import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  import ConversationRole._
  import Threading.Implicits.Background

  val messages      = mock[MessagesStorage]
  val storage       = mock[NotificationStorage]
  val conversations = mock[ConversationStorage]
  val pushService   = mock[PushService]
  val uiController  = mock[NotificationUiController]
  val userService   = mock[UserService]

  private val storedNotifications = Signal(Set.empty[NotificationData])
  private val processing = Signal(true)

  private def lastEventTime = RemoteInstant.apply(clock.instant())

  private val self       = UserData(account1Id, "")
  private val beDrift    = Signal(Duration.ZERO)
  private val rConvId    = RConvId("r-conv")
  private val convId     = ConvId("conv")
  private val conv       = ConversationData(remoteId = rConvId, id = convId)
  private val content    = TextMessage("abc")
  private val from       = UserId("User1")
  private lazy val event = GenericMessageEvent(rConvId, lastEventTime, from, content)

  private val msg = MessageData(
    MessageId(content.messageId),
    conv.id,
    msgType = Message.Type.TEXT,
    protos  = Seq(content),
    userId  = from,
    time    = lastEventTime
  )

  private val compositeMsg = MessageData(
    MessageId(content.messageId),
    conv.id,
    msgType = Message.Type.COMPOSITE,
    protos  = Seq(content),
    userId  = from,
    time    = lastEventTime
  )

  (pushService.processing _).expects().anyNumberOfTimes().onCall(_ => processing)

  (storage.list _).expects().anyNumberOfTimes().onCall { _ => storedNotifications.head.map(_.toSeq) }
  (storage.removeAll _).expects(*).anyNumberOfTimes().onCall { toRemove: Iterable[NotId] =>
    Future.successful[Unit] { storedNotifications.mutate { _.filterNot(n => toRemove.toSet.contains(n.id)) }; }
  }
  (storage.insertAll _).expects(*).anyNumberOfTimes().onCall { toAdd: Traversable[NotificationData] =>
    Future.successful {
      storedNotifications.mutate { _ ++ toAdd }
      Set.empty[NotificationData] //return not important
    }
  }

  (pushService.beDrift _).expects().anyNumberOfTimes().returning(beDrift)

  private val notificationsSourceVisible = Signal(Map.empty[UserId, Set[ConvId]])
  (uiController.notificationsSourceVisible _).expects().anyNumberOfTimes().returning(notificationsSourceVisible)

  private def getService =
    new NotificationServiceImpl(account1Id, messages, storage, conversations, pushService, uiController, userService, clock)

  private def setup(availability: Availability,
                    msgs:         Seq[MessageData],
                    findMsgsFrom: Seq[MessageData],
                    convs:        Seq[ConversationData],
                    eventTime:    Option[RemoteInstant]
                   ): Unit = {
    val convsMap = convs.toIdMap
    val rConvsMap = convs.map(c => c.remoteId -> c).toMap
    (messages.getAll _).expects(*).anyNumberOfTimes().returning(Future.successful(msgs.map(Option(_))))
    (conversations.getByRemoteId _).expects(*).anyNumberOfTimes().onCall { rId: RConvId => Future.successful(rConvsMap.get(rId)) }

    eventTime match {
      case Some(t) =>
        (messages.findMessagesFrom _).expects(*, t).anyNumberOfTimes().onCall { (cId: ConvId, _: RemoteInstant) =>
          Future.successful(findMsgsFrom.filter(_.convId == cId).toIndexedSeq)
        }
      case None =>
        (messages.findMessagesFrom _).expects(*, *).anyNumberOfTimes().onCall { (cId: ConvId, _: RemoteInstant) =>
          Future.successful(findMsgsFrom.filter(_.convId == cId).toIndexedSeq)
        }
    }

    // in pushNotificationToUI
    (userService.getSelfUser _).expects().anyNumberOfTimes().returning(
      Future.successful(Some(self.copy(availability = availability)))
    )
    (conversations.get _).expects(*).anyNumberOfTimes().onCall { cId: ConvId => Future.successful(convsMap.get(cId)) }
  }

  private def testCompositeMessageNotificationShown(availability: Availability): Unit = {
    processing ! true

    setup(
      availability = Availability.Available,
      msgs         = Seq(msg),
      findMsgsFrom = Seq(compositeMsg),
      convs        = Seq(conv),
      eventTime    = Some(lastEventTime)
    )

    (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
      nots.size shouldEqual 1
      nots.head.msgType shouldEqual NotificationType.COMPOSITE
      Future.successful({})
    }

    result(getService.messageNotificationEventsStage(rConvId, Vector(event)))

    processing ! false

    awaitAllTasks
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    storedNotifications ! Set.empty

    //advance the clock to avoid weird problems around the epoch (e.g.)
    clock + 24.hours
  }

  feature("Message events") {
    scenario("Process basic text message notifications") {
      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(msg),
        findMsgsFrom = Seq(msg),
        convs        = Seq(conv),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 1
        nots.head.msg shouldEqual "abc"
        nots.head.hasBeenDisplayed shouldEqual false
        Future.successful({})
      }

      val service = getService

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Don't push standard notifications to UI when the user is away") {
      processing ! true

      setup(
        availability = Availability.Away,
        msgs         = Seq(msg),
        findMsgsFrom = Seq(msg),
        convs        = Seq(conv),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 0
        Future.successful({})
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Don't push standard notifications to UI when the user is busy and the message is not a reply/mention") {
      processing ! true

      setup(
        availability = Availability.Busy,
        msgs         = Seq(msg),
        findMsgsFrom = Seq(msg),
        convs        = Seq(conv),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 0
        Future.successful({})
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Push notifications to UI when the user is busy and the message is a reply/mention") {
      processing ! true

      val origMsg = MessageData(
        MessageId("orig"),
        conv.id,
        msgType = Message.Type.TEXT,
        protos  = Seq(content),
        userId  = self.id,
        time    = lastEventTime
      )
      val reply = MessageData(
        MessageId(content.messageId),
        conv.id,
        msgType = Message.Type.TEXT,
        protos  = Seq(content),
        userId  = from,
        time    = lastEventTime,
        quote   = Some(QuoteContent(origMsg.id, validity = true, hash = None))
      )

      setup(
        availability = Availability.Busy,
        msgs         = Seq(origMsg),
        findMsgsFrom = Seq(origMsg, reply),
        convs        = Seq(conv),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 1
        nots.head.msg shouldEqual "abc"
        nots.head.hasBeenDisplayed shouldEqual false
        Future.successful({})
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Push composite message notifications to UI even when the user is away") {
      testCompositeMessageNotificationShown(Availability.Away)
    }

    scenario("Push composite message notifications to UI even when the user is busy") {
      testCompositeMessageNotificationShown(Availability.Busy)
    }

    scenario("Notifications are only pushed to UI for conversations with correct mute states") {
      val rConvId2 = RConvId("conv2")
      val conv = ConversationData(ConvId("conv"), rConvId, muted = MuteSet.AllMuted)
      val conv2 = ConversationData(ConvId("conv2"), rConvId2, muted = MuteSet.OnlyMentionsAllowed)

      //prefil notification storage
      val previousNots = Set(
        NotificationData(hasBeenDisplayed = true, conv = conv.id, time = lastEventTime),
        NotificationData(hasBeenDisplayed = true, conv = conv2.id, time = lastEventTime),
        NotificationData(hasBeenDisplayed = true, conv = conv2.id, time = lastEventTime, isReply = true),
        NotificationData(hasBeenDisplayed = true, conv = conv2.id, time = lastEventTime, isSelfMentioned = true)
      )
      storedNotifications ! previousNots

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(msg),
        findMsgsFrom = Seq(msg),
        convs        = Seq(conv, conv2),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 2
        Future.successful({})
      }

      val service = getService

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Previous notifications that have not been dismissed are passed with notifications from new events") {
      //prefil notification storage
      val previousNots = Set(
        NotificationData(hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant.apply(clock.instant)),
        NotificationData(hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant.apply(clock.instant))
      )
      storedNotifications ! previousNots

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(msg),
        findMsgsFrom = Seq(msg),
        convs        = Seq(conv),
        eventTime    = Some(lastEventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 3
        nots.exists(_.msg == "abc") shouldEqual true
        val (shown, toShow) = nots.partition(_.hasBeenDisplayed)
        shown.size shouldEqual 2
        toShow.size shouldEqual 1
        Future.successful({})
      }

      val service = getService

      result(service.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

    scenario("Apply multiple message edit events to previous notifications") {
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
        id               = NotId(originalContent.messageId),
        msg              = "abc",
        conv             = conv.id,
        user             = from,
        msgType          = NotificationType.TEXT,
        time             = origEventTime,
        hasBeenDisplayed = true
      )

      storedNotifications ! Set(originalNotification)

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(msg),
        findMsgsFrom = Nil,
        convs        = Seq(conv),
        eventTime    = Some(edit1EventTime)
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        val not = nots.head
        not.id shouldEqual NotId(editContent2.messageId)
        not.msg shouldEqual "ghi"
        Future.successful({})
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(editEvent1, editEvent2)))

      processing ! false

      awaitAllTasks
    }

    scenario("Apply delete and recall (hide and delete) events to previous notifications in storage and stream") {

      //prefil notification storage
      val toBeDeletedNotif = NotificationData(NotId("not-id-1"), hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant(clock.instant))
      val remainingNotif = NotificationData(NotId("not-id-2"), hasBeenDisplayed = true, conv = conv.id, time = RemoteInstant(clock.instant))

      val previousNots = Set(
        toBeDeletedNotif,
        remainingNotif
      )
      storedNotifications ! previousNots

      processing ! true

      val from = UserId("User1")
      val msgContent = GenericMessage(Uid("messageId"), Text("abc"))
      val msgEvent = GenericMessageEvent(rConvId, RemoteInstant(clock.instant()), from, msgContent)

      val deleteContent1 = GenericMessage(Uid(), MsgDeleted(rConvId, MessageId(toBeDeletedNotif.id.str)))
      val deleteEvent1 = GenericMessageEvent(rConvId, RemoteInstant.apply(clock.instant()), from, deleteContent1)

      val deleteContent2 = GenericMessage(Uid(), MsgRecall(MessageId(msgContent.messageId)))
      val deleteEvent2 = GenericMessageEvent(rConvId, RemoteInstant.apply(clock.instant()), from, deleteContent2)

      setup(
        availability = Availability.Available,
        msgs         = Seq(msg),
        findMsgsFrom = Nil,
        convs        = Seq(conv),
        eventTime    = None
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        Future.successful {
          nots.size shouldEqual 1
          nots.head shouldEqual remainingNotif
        }
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(msgEvent, deleteEvent1, deleteEvent2)))

      processing ! false

      awaitAllTasks
    }

    scenario("Multiple alternative likes and unlikes only ever apply the last event") {

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

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(originalMessage),
        findMsgsFrom = Nil,
        convs        = Seq(conv),
        eventTime    = None
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).onCall { (_, nots) =>
        nots.size shouldEqual 2
        nots.foreach { n =>
          n.msgType shouldEqual NotificationType.LIKE
        }
        nots.map(_.id).contains(NotId(s"$LIKE-${likedMessageId.str}-${from.str}")) shouldEqual true
        nots.map(_.id).contains(NotId(s"$LIKE-${likedMessageId.str}-${from2.str}")) shouldEqual true
        Future.successful({})
      }

      result(getService.messageNotificationEventsStage(rConvId, Vector(like1Event, unlikeEvent, otherLikeEvent, like2Event)))

      processing ! false

      awaitAllTasks
    }
  }

  feature ("Conversation state events") {

    scenario("Group creation events") {
      val generatedMessageId = MessageId()
      val event = CreateConversationEvent(rConvId, RemoteInstant(clock.instant()), from, ConversationResponse(
        rConvId, Some(Name("conv")), from, ConversationType.Group, None, MuteSet.AllAllowed,
        RemoteInstant.Epoch, archived = false, RemoteInstant.Epoch, Set.empty, None, None, None,
        Map(account1Id -> MemberRole, from -> AdminRole), None
      ))

      val memberJoinMsg = MessageData(
        generatedMessageId,
        conv.id,
        msgType = Message.Type.MEMBER_JOIN,
        userId  = from,
        time    = lastEventTime
      )

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Seq(memberJoinMsg),
        findMsgsFrom = Seq(memberJoinMsg),
        convs        = Seq(conv),
        eventTime    = None
      )

      (uiController.onNotificationsChanged _).expects(account1Id, *).never().returning(Future.successful({}))

      result(getService.messageNotificationEventsStage(rConvId, Vector(event)))

      processing ! false

      awaitAllTasks
    }

  }

  feature("Dismissing notifications when their conversation is seen") {

    scenario("Seeing conversation removes all notifications for that conversation and updates UI") {

      val conv1 = ConversationData()
      val conv2 = ConversationData()

      val not1 = NotificationData(hasBeenDisplayed = true, conv = conv1.id, time = RemoteInstant.apply(clock.instant))
      val not2 = NotificationData(hasBeenDisplayed = true, conv = conv1.id, time = RemoteInstant.apply(clock.instant))
      val not3 = NotificationData(hasBeenDisplayed = true, conv = conv2.id, time = RemoteInstant.apply(clock.instant)) //a different conv!
      //prefil notification storage
      val previousNots = Set(not1, not2, not3)
      storedNotifications ! previousNots

      processing ! true

      setup(
        availability = Availability.Available,
        msgs         = Nil,
        findMsgsFrom = Nil,
        convs        = Seq(conv1, conv2),
        eventTime    = None
      )

      getService

      notificationsSourceVisible ! Map(account1Id -> Set(conv1.id))

      processing ! false

      awaitAllTasks

      result(storedNotifications.head) shouldEqual Set(not3)
    }
  }
}
