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

import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.UiLifeCycle
import com.waz.service.push.{GlobalNotificationsService, GlobalNotificationsServiceImpl, NotificationService, PushService}
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.TestUserPreferences
import com.waz.utils.RichFiniteDuration
import com.waz.utils.events.{EventStream, Signal}
import org.threeten.bp.Duration

import scala.concurrent.duration._
import scala.concurrent.{Future, duration}

class NotificationsServiceSpec extends AndroidFreeSpec {
  import com.waz.threading.Threading.Implicits.Background

  val self      = UserId()
  val messages  = mock[MessagesStorage]
  val lifeCycle = mock[UiLifeCycle]
  val storage   = mock[NotificationStorage]
  val users     = mock[UsersStorage]
  val convs     = mock[ConversationStorage]
  val reactions = mock[ReactionsStorage]
  val userPrefs = new TestUserPreferences
  val push      = mock[PushService]
  val members   = mock[MembersStorage]
  val globalNots: GlobalNotificationsService = new GlobalNotificationsServiceImpl

  val messagesInStorage = Signal(Map[MessageId, MessageData]()).disableAutowiring()

  (messages.getAll _).expects(*).anyNumberOfTimes.onCall { ids: Traversable[MessageId] =>
    messagesInStorage.head.map(msgs => ids.map(msgs.get).toSeq )
  }

  val inForeground = Signal(false)
  val beDrift      = Signal(Duration.ZERO)
  val currentConv  = Signal(Option.empty[ConvId])
  val convsAdded   = EventStream[Seq[ConversationData]]()
  val convsUpdated = EventStream[Seq[(ConversationData, ConversationData)]]()
  val allConvs     = Signal[IndexedSeq[ConversationData]]()

  val notifications = Signal[Map[NotId, NotificationData]]()

  val msgsAdded   = EventStream[Seq[MessageData]]()
  val msgsUpdated = EventStream[Seq[(MessageData, MessageData)]]()
  val msgsDeleted = EventStream[Seq[MessageId]]()

  val reactionsChanged = EventStream[Seq[Liking]]()
  val reactionsDeleted = EventStream[Seq[(MessageId, UserId)]]()

  NotificationService.ClearThrottling = duration.Duration.Zero

  feature ("Background behaviour") {

    scenario("Display notifications that arrive after account becomes inactive that have not been read elsewhere") {
      val user = UserData(UserId("user"), "testUser1")
      val conv = ConversationData(ConvId("conv"), RConvId(), Some(Name("conv")), user.id, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
      fillMembers(conv, Seq(user.id))
      allConvs ! IndexedSeq(conv)

      inForeground ! false
      clock + 10.seconds //messages arrive some time after the account was last visible

      val msg1 = MessageData(MessageId("msg1"), conv.id, Message.Type.TEXT, user.id)
      val msg2 = MessageData(MessageId("msg2"), conv.id, Message.Type.TEXT, user.id)

      (users.get _).expects(user.id).twice.returning(Future.successful(Some(user)))
      (convs.get _).expects(conv.id).twice.returning(Future.successful(Some(conv)))

      val service = getService

      msgsAdded ! Seq(msg1, msg2)

      result(service.notifications.filter(_.size == 2).head)
    }

    scenario("Showing the conversation list should clear the current account notifications") {
      val user = UserData(UserId("user"), "testUser1")
      val conv = ConversationData(ConvId("conv"), RConvId(), Some(Name("conv")), user.id, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
      fillMembers(conv, Seq(user.id))
      allConvs ! IndexedSeq(conv)

      inForeground ! false
      clock + 10.seconds //messages arrive some time after the account was last visible

      val msg1 = MessageData(MessageId("msg1"), conv.id, Message.Type.TEXT, user.id)
      val msg2 = MessageData(MessageId("msg2"), conv.id, Message.Type.TEXT, user.id)

      (users.get _).expects(user.id).twice.returning(Future.successful(Some(user)))
      (convs.get _).expects(conv.id).twice.returning(Future.successful(Some(conv)))

      val service = getService

      msgsAdded ! Seq(msg1, msg2)

      result(service.notifications.filter(_.size == 2).head)

      clock + 10.seconds
      inForeground ! true
      globalNots.notificationsSourceVisible ! scala.collection.immutable.Map((self, scala.Predef.Set(conv.id)))

      result(service.notifications.filter(_.isEmpty).head)
    }

    scenario("Receiving notifications after app is put to background should take BE drift into account") {

      val user = UserData(UserId("user"), "testUser1")
      val conv = ConversationData(ConvId("conv"), RConvId(), Some(Name("conv")), user.id, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
      fillMembers(conv, Seq(user.id))
      allConvs ! IndexedSeq(conv)

      clock + 15.seconds
      val drift = -15.seconds
      beDrift ! drift.asJava

      inForeground ! true

      val service = getService

      inForeground ! false

      clock + 10.seconds //messages arrive at some point later but within drift time
      val msg1 = MessageData(MessageId("msg1"), conv.id, Message.Type.TEXT, user.id, time = LocalInstant.Now.toRemote(drift))
      val msg2 = MessageData(MessageId("msg2"), conv.id, Message.Type.TEXT, user.id, time = LocalInstant.Now.toRemote(drift))

      (users.get _).expects(user.id).twice.returning(Future.successful(Some(user)))
      (convs.get _).expects(conv.id).twice.returning(Future.successful(Some(conv)))

      msgsAdded ! Seq(msg1, msg2)

      result(service.notifications.filter(_.size == 2).head)
    }
  }

  scenario("Notifications for the current displaying conversation shouldn't be created") {
    val user = UserData(UserId("user"), "testUser1")
    val conv = ConversationData(ConvId("conv"), RConvId(), Some(Name("conv")), user.id, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
    val conv2 = ConversationData(ConvId("conv2"), RConvId(), Some(Name("conv2")), user.id, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
    fillMembers(conv, Seq(user.id))
    fillMembers(conv2, Seq(user.id))
    allConvs ! IndexedSeq(conv)

    inForeground ! true
    currentConv ! Some(conv2.id)
    globalNots.notificationsSourceVisible ! scala.collection.immutable.Map((self, scala.Predef.Set(conv2.id)))
    clock + 10.seconds //messages arrive some time after the account was last visible

    val msg1 = MessageData(MessageId("msg1"), conv.id, Message.Type.TEXT, user.id)
    val msg2 = MessageData(MessageId("msg2"), conv2.id, Message.Type.TEXT, user.id)

    (users.get _).expects(user.id).once.returning(Future.successful(Some(user)))
    (convs.get _).expects(conv.id).once.returning(Future.successful(Some(conv)))

    val service = getService

    msgsAdded ! Seq(msg1, msg2)

    result(service.notifications.filter(nots => nots.size == 1 && nots.exists(_.convId == conv.id)).head)
  }

  scenario("Create a notification for a quote message") {
    val otherUserId = UserId("user1")
    val otherUser = UserData(otherUserId, "otherUser")
    val conv = ConversationData(ConvId("conv"), RConvId(), Some("conv"), otherUserId, ConversationType.OneToOne, lastRead = RemoteInstant.Epoch)
    fillMembers(conv, Seq(otherUserId, self))
    allConvs ! IndexedSeq(conv)
    inForeground ! false
    clock + 10.seconds //messages arrive some time after the account was last visible

    val otherMsgId = MessageId("other msg")
    val otherMsg = MessageData(otherMsgId, conv.id, Message.Type.TEXT, self)
    val msg = MessageData(MessageId("msg"), conv.id, Message.Type.TEXT, otherUserId, quote = Some(QuoteContent(otherMsgId, validity = true)))

    (users.get _).expects(otherUserId).anyNumberOfTimes.returning(Future.successful(Some(otherUser)))
    (convs.get _).expects(conv.id).anyNumberOfTimes.returning(Future.successful(Some(conv)))

    messagesInStorage ! Map(otherMsgId -> otherMsg)


    val service = getService

    msgsAdded ! Seq(msg)

    result(service.notifications.filter(nots => nots.size == 1 && nots.exists(n => n.convId == conv.id && n.isQuote)).head)
  }


  def fillMembers(conv: ConversationData, users: Seq[UserId]) = {
    (members.getByConv _).expects(conv.id).anyNumberOfTimes().returning(Future.successful((users :+ self).map(uid => ConversationMemberData(uid, conv.id)).toIndexedSeq))
  }

  def getService = {

    (storage.contents _).expects().anyNumberOfTimes().returning(notifications)

    (storage.insertAll _).expects(*).anyNumberOfTimes().onCall { nots: Traversable[NotificationData] =>
      notifications ! nots.map(n => n.id -> n).toMap
      Future.successful(nots.toSet)
    }

    (storage.removeAll _).expects(*).anyNumberOfTimes().onCall { keys: Traversable[NotId] =>
      notifications.mutate(_ -- keys.toSet)
      Future.successful({})
    }

    (convs.onAdded _).expects().returning(convsAdded)
    (convs.onUpdated _).expects().returning(convsUpdated)
    (convs.list _).expects().returning(allConvs.head)

    (messages.onAdded _).expects().returning(msgsAdded)
    (messages.onUpdated _).expects().returning(msgsUpdated)
    (messages.onDeleted _).expects().returning(msgsDeleted)

    (reactions.onChanged _).expects().returning(reactionsChanged)
    (reactions.onDeleted _).expects().returning(reactionsDeleted)

    (push.beDrift _).expects().anyNumberOfTimes().returning(Signal.const(Duration.ZERO))

    new NotificationService(null, self, messages, lifeCycle, storage, users, convs, members, reactions, userPrefs, push, globalNots)
  }

}
