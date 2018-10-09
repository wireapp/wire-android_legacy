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

import com.waz.api.Message
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.ConversationData.ConversationType.WaitForConnection
import com.waz.model.UserData.ConnectionStatus._
import com.waz.model._
import com.waz.service._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.returning
import org.scalatest.Inside

import scala.concurrent.Future

class ConnectionServiceSpec extends AndroidFreeSpec with Inside {

  implicit val executionContext = new SerialDispatchQueue(name = "ConnectionServiceAndroidFreeSpec")

  val push            = mock[PushService]
  val teamId          = Option.empty[TeamId]
  val convs           = mock[ConversationsContentUpdater]
  val members         = mock[MembersStorage]
  val messagesService = mock[MessagesService]
  val messagesStorage = mock[MessagesStorage]
  val users           = mock[UserService]
  val usersStorage    = mock[UsersStorage]
  val sync            = mock[SyncServiceHandle]

  val convsStorage    = mock[ConversationStorage]

  val rConvId        = RConvId("remote-conv-id")
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

      updatedConv.convType should be(WaitForConnection)
    }
  }

  feature ("connect to user") {

    scenario("connect to user") {

      val userToConnectId = UserId("user-id")
      val userToConnect = UserData(userToConnectId, name = "name", searchKey = SearchKey.simple("name"))

      (users.getOrCreateUser _).expects(userToConnectId).returning(Future.successful(userToConnect))
      (users.updateConnectionStatus _).expects(userToConnectId, PendingFromUser, None, None).returning(Future.successful(Some(userToConnect.copy(connection = PendingFromUser))))
      (sync.postConnection _).expects(userToConnect.id, *, *).returning(Future.successful(SyncId()))

      (usersStorage.listAll _).expects(*).anyNumberOfTimes().onCall { ids: Traversable[UserId] =>
        Future.successful(
          ids.collect {
            case id if id == userToConnectId => userToConnect
          }.toVector
        )
      }

      (convsStorage.getByRemoteIds2 _).expects(*).twice().returning(Future.successful(Map.empty))
      (convsStorage.updateLocalIds _).expects(Map.empty[ConvId, ConvId]).returning(Future.successful(Set.empty))
      (convsStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ((ConvId, Option[ConversationData]) => ConversationData)) =>
        Future.successful(keys.map(id => updater(id, None)).toSet)
      }
      (messagesService.addConnectRequestMessage _)
        .expects(ConvId(userToConnectId.str), selfUserId, userToConnectId, "", userToConnect.name, false)
        .returning(Future.successful(MessageData()))

      val service = createBlankService()

      inside(result(service.connectToUser(userToConnectId, "", userToConnect.name))) {
        case Some(conv) =>
          conv.id shouldEqual ConvId(userToConnectId.str)
          conv.remoteId shouldEqual RConvId(userToConnectId.str)
          conv.creator shouldEqual selfUserId
          conv.convType shouldEqual WaitForConnection
          conv.hidden shouldEqual false
        case None => fail("No conversation was created")
      }
    }

    scenario("handle response after sending connect request sync job completes - ensure that remoteId of previously created conversation is updated properly") {

      val remoteId = RConvId("remote_conv")
      var otherUser = UserData("other-user").copy()

      val tempRemoteId = RConvId(otherUser.id.str)
      val convId = ConvId(otherUser.id.str)

      var previousConv = ConversationData(convId, tempRemoteId, convType = WaitForConnection)
      println(previousConv)

      (usersStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[UserId], updater: ((UserId, Option[UserData]) => UserData)) =>
        Future.successful(keys.map {
          case id if id == otherUser.id =>
            returning(updater(id, Some(otherUser)))(otherUser = _)
          case _ => fail("Unexpected user being updated")
        }.toSet)
      }

      (sync.syncUsers _).expects(Set(otherUser.id)).returning(Future.successful(SyncId()))
      (usersStorage.listAll _).expects(*).returning(Future.successful(Vector(otherUser)))
      (convsStorage.getByRemoteIds2 _).expects(Set(remoteId)).twice().returning(Future.successful(Map.empty))
      (convsStorage.updateLocalIds _).expects(Map.empty[ConvId, ConvId]).returning(Future.successful(Set.empty))
      (convsStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ((ConvId, Option[ConversationData]) => ConversationData)) =>
        Future.successful(keys.map {
          case id if id == convId =>
            returning(updater(id, Some(previousConv))) { updated =>
              println(updated)
              previousConv = updated
            }
          case _ => fail("Unexpected user being updated")
        }.toSet)
      }
      (members.addAll _).expects(Map(convId -> Set(otherUser.id, selfUserId))).returning(Future.successful({}))
      (convsStorage.updateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ConversationData => ConversationData) =>
        if (!keys.toSet.contains(convId)) fail ("didn't try to update other conversation")
        val prev = previousConv
        Future.successful(Seq((prev, returning(updater(previousConv)) { updated =>
          println(updated)
          previousConv = updated
        })))
      }
      (messagesStorage.getLastMessage _).expects(convId).returning(Future.successful(Some(MessageData()))) //shouldn't be needed


      val service = createBlankService()
      await(service.handleUserConnectionEvents(Seq(UserConnectionEvent(remoteId, selfUserId, otherUser.id, Some("Hi!"), PendingFromUser, RemoteInstant(clock.instant()), None))))

      previousConv.remoteId shouldEqual remoteId

    }
  }

  def getUpdatedConversation(service: ConnectionServiceImpl, event: UserConnectionEvent): ConversationData = {
    var updatedConversation = ConversationData.Empty

    (convsStorage.updateAll2 _).expects(*,*).once().onCall { (convIds, updater) =>
      val old = ConversationData(convIds.head, RConvId(convIds.head.str), None, selfUserId, ConversationType.Unknown, lastEventTime = RemoteInstant.Epoch)
      updatedConversation = updater(old)
      Future.successful(Seq((old, updatedConversation)))
    }

    result(service.handleUserConnectionEvents(Seq(event)))
    updatedConversation
  }

  def createBlankService() =
   new ConnectionServiceImpl(selfUserId, teamId, push, convs, convsStorage, members, messagesService, messagesStorage, users, usersStorage, sync)

  def initConnectionService(): ConnectionServiceImpl = {

    (usersStorage.listAll _).expects(*).anyNumberOfTimes().onCall { ids: Traversable[UserId] =>
      Future.successful(
        ids.collect {
          case id if id == otherUserId => UserData(otherUserId, name = "other-user", searchKey = SearchKey.simple("other-user"))
        }.toVector
      )
    }

    (convsStorage.getByRemoteIds2 _).expects(*).anyNumberOfTimes().returning(Future.successful(Map.empty))
    (convsStorage.updateLocalIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
    (convsStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ((ConvId, Option[ConversationData]) => ConversationData)) =>
      Future.successful(keys.map(id => updater(id, None)).toSet)
    }

    (usersStorage.updateOrCreate _).expects(*,*,*).anyNumberOfTimes().onCall{ (_, _, creator) =>
        Future.successful(creator)
    }
    (usersStorage.updateOrCreateAll2 _).expects(*,*).anyNumberOfTimes().onCall{ (uIds, creator) =>
        Future.successful(uIds.map(creator(_, None)).toSet)
    }
    (usersStorage.get _).expects(*).anyNumberOfTimes().onCall{uId: UserId => Future.successful(Some(UserData(uId, "")))}

    (members.addAll (_:Map[ConvId, Set[UserId]])).expects(*).anyNumberOfTimes().returning(Future.successful(()))

    (messagesStorage.getLastMessage _).expects(*).anyNumberOfTimes().returns(Future.successful(None))

    (messagesService.addDeviceStartMessages _).expects(*, *).anyNumberOfTimes().onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
      Future.successful(convs.map(conv => MessageData(MessageId(), conv.id, Message.Type.STARTED_USING_DEVICE, selfUserId)).toSet)
    }
    (sync.syncUsers _).expects(*).anyNumberOfTimes().returns(Future.successful(SyncId()))
    new ConnectionServiceImpl(selfUserId, teamId, push, convs, convsStorage, members, messagesService, messagesStorage, users, usersStorage, sync)
  }
}
