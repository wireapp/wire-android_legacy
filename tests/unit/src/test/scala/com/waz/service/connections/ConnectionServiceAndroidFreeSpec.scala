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
package com.waz.service.connections

import java.util.Date

import com.waz.api.Message
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus._
import com.waz.model._
import com.waz.service._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.threading.SerialDispatchQueue
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConnectionServiceAndroidFreeSpec extends AndroidFreeSpec {

  implicit val executionContext = new SerialDispatchQueue(name = "ConnectionServiceAndroidFreeSpec")

  val push            = mock[PushService]
  val convs           = mock[ConversationsContentUpdater]
  val members         = mock[MembersStorage]
  val messagesService = mock[MessagesService]
  val messagesStorage = mock[MessagesStorage]
  val users           = mock[UserService]
  val usersStorage    = mock[UsersStorage]
  val sync            = mock[SyncServiceHandle]

  val convsStorage    = mock[ConversationStorage]

  val rConvId        = RConvId()
  val selfUserId     = UserId("selfUserId")
  val otherUserId    = UserId("otherUserId")

  feature("Event handling") {

    scenario("Handle connection events updates the last event time of the conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, selfUserId, otherUserId, None, Accepted, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.lastEventTime should be(event.lastUpdated)
    }

    scenario("Handling an accepted connection event should return a one to one conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, selfUserId, otherUserId, None, Accepted, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(ConversationType.OneToOne)
    }

    scenario("Handling a pending from other connection event should return a wait for connection conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, selfUserId, otherUserId, None, PendingFromOther, RemoteInstant.ofEpochMilli(1))

      (messagesService.addConnectRequestMessage _).expects(*, *, *, *, *, *).once().returns(Future.successful(MessageData.Empty))

      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(ConversationType.Incoming)
    }

    scenario("Handling a pending from user connection event should return a incoming conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, selfUserId, otherUserId, None, PendingFromUser, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(ConversationType.WaitForConnection)
    }
  }

  def getUpdatedConversation(service: ConnectionServiceImpl, event: UserConnectionEvent): ConversationData = {
    var updatedConversation = ConversationData.Empty

    (convsStorage.update _).expects(*,*).once().onCall { (convId, updater) =>
      val old = ConversationData(convId, RConvId(convId.str), None, selfUserId, ConversationType.Unknown, lastEventTime = RemoteInstant.Epoch)
      updatedConversation = updater(old)
      Future.successful(Some(old, updatedConversation))
    }

    result(service.handleUserConnectionEvents(Seq(event)))
    updatedConversation
  }

  def initConnectionService(): ConnectionServiceImpl = {
    (convs.storage _).expects().once().returning(convsStorage)
    (usersStorage.updateOrCreate _).expects(*,*,*).anyNumberOfTimes().onCall{ (_, _, creator) =>
        Future.successful(creator)
    }
    (usersStorage.get _).expects(*).anyNumberOfTimes().onCall{uId: UserId => Future.successful(Some(UserData(uId, "")))}
    (convs.getOneToOneConversation _).expects(*, *, *, *).anyNumberOfTimes().onCall{(toUser, selfUserId, remoteId, tpe) =>
      val rId = remoteId.getOrElse(RConvId(toUser.str))
      val convId = ConvId(rId.str)
      Future.successful(ConversationData(convId, rId, None, selfUserId, tpe))
    }
    (members.add (_:ConvId, _:Iterable[UserId])).expects(*, *).anyNumberOfTimes().onCall { (conv: ConvId, users: Iterable[UserId]) =>
      Future.successful(users.map(uId => ConversationMemberData(uId, conv)).toSet)
    }
    (messagesStorage.getLastMessage _).expects(*).anyNumberOfTimes().returns(Future.successful(None))

    (messagesService.addDeviceStartMessages _).expects(*, *).anyNumberOfTimes().onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
      Future.successful(convs.map(conv => MessageData(MessageId(), conv.id, Message.Type.STARTED_USING_DEVICE, selfUserId)).toSet)
    }
    (sync.syncUsers _).expects(*).anyNumberOfTimes().returns(Future.successful(SyncId()))
    new ConnectionServiceImpl(selfUserId, push, convs, members, messagesService, messagesStorage, users, usersStorage, sync)
  }
}
