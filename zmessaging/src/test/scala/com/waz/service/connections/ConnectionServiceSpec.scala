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

import com.waz.api.{ConnectionStatus, Message}
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.ConversationData.ConversationType._
import com.waz.model.UserData.ConnectionStatus._
import com.waz.model._
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.MessagesService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.utils.returning
import org.scalatest.Inside

import scala.concurrent.Future

class ConnectionServiceSpec extends AndroidFreeSpec with Inside {
  val federationSupported: Boolean = false
  import com.waz.threading.Threading.Implicits.Background

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
  val domain        = if (federationSupported) Domain("chala.wire.link") else Domain.Empty

  lazy val user =
    if (federationSupported)
      UserData(UserId("user-id"), domain = domain, name = Name("name"), searchKey = SearchKey.simple("name"))
    else
      UserData(UserId("user-id"), name = Name("name"), searchKey = SearchKey.simple("name"))

  feature("Event handling") {

    scenario("Handle connection events updates the last event time of the conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, domain, selfUserId, otherUserId, domain, None, Accepted, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.lastEventTime should be(event.lastUpdated)
    }

    scenario("Handling an accepted connection event should return a one to one conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, domain, selfUserId, otherUserId, domain, None, Accepted, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(ConversationType.OneToOne)
    }

    scenario("Handling a pending from other connection event should return a wait for connection conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, domain, selfUserId, otherUserId, domain, None, PendingFromOther, RemoteInstant.ofEpochMilli(1))

      (messagesService.addConnectRequestMessage _).expects(*, *, *, *, *, *).once().returns(Future.successful(MessageData.Empty))

      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(ConversationType.Incoming)
    }

    scenario("Handling a pending from user connection event should return a incoming conversation") {
      val service = initConnectionService()
      val event = UserConnectionEvent(rConvId, domain, selfUserId, otherUserId, domain, None, PendingFromUser, RemoteInstant.ofEpochMilli(1))
      val updatedConv = getUpdatedConversation(service, event)

      updatedConv.convType should be(WaitForConnection)
    }
  }

  feature ("connect to user") {

    def setup(user: UserData, expectedNewStatus: ConnectionStatus) = {
      if (federationSupported) {
        (users.getOrCreateQualifiedUser _).expects(user.qualifiedId.get, *).anyNumberOfTimes().returning(Future.successful(user))
      } else {
        (users.getOrCreateUser _).expects(user.id, *).anyNumberOfTimes().returning(Future.successful(user))
      }
      (users.updateConnectionStatus _).expects(user.id, expectedNewStatus, None, None).anyNumberOfTimes().returning(
        Future.successful(Some(user.copy(connection = expectedNewStatus)))
      )
      (users.qualifiedId _).expects(user.id).anyNumberOfTimes().returning(Future.successful(QualifiedId(user.id, domain)))
      if (federationSupported) {
        val qId = QualifiedId(user.id, domain)
        (sync.postQualifiedConnection _).expects(qId).anyNumberOfTimes().returning(Future.successful(SyncId()))
        (sync.postQualifiedConnectionStatus _).expects(qId, expectedNewStatus).anyNumberOfTimes().returning(Future.successful(SyncId()))
      } else {
        (sync.postConnection _).expects(user.id, *, *).anyNumberOfTimes().returning(Future.successful(SyncId()))
        (sync.postConnectionStatus _).expects(user.id, expectedNewStatus).anyNumberOfTimes().returning(Future.successful(SyncId()))
      }

      (usersStorage.listAll _).expects(*).anyNumberOfTimes().onCall { ids: Traversable[UserId] =>
        Future.successful(
          ids.collect {
            case id if id == user.id => user
          }.toVector
        )
      }

      (convsStorage.getMapByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Map.empty))
      (convsStorage.updateLocalIds _).expects(Map.empty[ConvId, ConvId]).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (convsStorage.updateOrCreateAll2 _).expects(*, *).anyNumberOfTimes().onCall { (keys: Iterable[ConvId], updater: (ConvId, Option[ConversationData]) => ConversationData) =>
        Future.successful(keys.map(id => updater(id, None)).toSet)
      }
      (messagesService.addConnectRequestMessage _)
        .expects(ConvId(user.id.str), selfUserId, user.id, "", user.name, false)
        .anyNumberOfTimes()
        .returning(Future.successful(MessageData()))

      (convs.updateConversation _).expects(ConvId(user.id.str), *, *).anyNumberOfTimes().returning(Future.successful(None))
      // MessagesService.addMemberJoinMessage(user-id, selfUserId, Set(selfUserId), true, false)
      (messagesService.addMemberJoinMessage _).expects(ConvId(user.id.str), selfUserId, Set(selfUserId), *, *).anyNumberOfTimes().returning(Future.successful(None))

      createBlankService()
    }

    scenario("connect to user") {
      val service = setup(user, PendingFromUser)

      inside(result(service.connectToUser(user.id, "", user.name))) {
        case Some(conv) =>
          conv.id shouldEqual ConvId(user.id.str)
          conv.remoteId shouldEqual RConvId(user.id.str)
          conv.creator shouldEqual selfUserId
          conv.convType shouldEqual WaitForConnection
          conv.hidden shouldEqual false
        case None => fail("No conversation was created")
      }
    }

    scenario("accept the connection") {
      val service = setup(user, Accepted)

      inside(result(service.acceptConnection(user.id))) { case conv =>
          conv.id shouldEqual ConvId(user.id.str)
          conv.remoteId shouldEqual RConvId(user.id.str)
          conv.creator shouldEqual selfUserId
          conv.convType shouldEqual OneToOne
          conv.hidden shouldEqual false
      }
    }

    scenario("cancel the connection") {
      val pendingFromUser = user.copy(connection = PendingFromUser)
      (users.updateUserData _).expects(user.id, *).anyNumberOfTimes().onCall { (userId: UserId, updater: UserData => UserData) =>
        Future.successful(Some((pendingFromUser, updater(pendingFromUser))))
      }

      val service = setup(user, PendingFromUser)
      if (federationSupported) {
        (sync.postQualifiedConnectionStatus _).expects(user.qualifiedId.get, Cancelled).once().returning(Future.successful(SyncId()))
      } else {
        (sync.postConnectionStatus _).expects(user.id, Cancelled).once().returning(Future.successful(SyncId()))
      }
      (convs.setConversationHidden _).expects(ConvId(user.id.str), true).once().returning(Future.successful(None))
      val combined = for {
        _         <- service.connectToUser(user.id, "", user.name)
        Some(res) <- service.cancelConnection(user.id)
      } yield res

      inside(result(combined)) { case resUser =>
        resUser.id shouldEqual user.id
        resUser.connection shouldEqual Cancelled
      }
    }

    scenario("ignore the connection") {
      val pendingFromUser = user.copy(connection = PendingFromUser)
      (users.updateUserData _).expects(user.id, *).anyNumberOfTimes().onCall { (userId: UserId, updater: UserData => UserData) =>
        Future.successful(Some((pendingFromUser, updater(pendingFromUser))))
      }

      val service = setup(user, PendingFromUser)
      (users.updateConnectionStatus _).expects(user.id, Ignored, *, *).once().returning(Future.successful(Some(user.copy(connection = Ignored))))
      if (federationSupported) {
        (sync.postQualifiedConnectionStatus _).expects(user.qualifiedId.get, Ignored).once().returning(Future.successful(SyncId()))
      } else {
        (sync.postConnectionStatus _).expects(user.id, Ignored).once().returning(Future.successful(SyncId()))
      }
      (convs.hideIncomingConversation _).expects(user.id).once().returning(Future.successful(None))

      inside(result(service.ignoreConnection(user.id))) { case Some(resUser) =>
        resUser.id shouldEqual user.id
        resUser.connection shouldEqual Ignored
      }
    }

    scenario("block the connection") {
      val blockedUser = user.copy(connection = Blocked)
      (users.updateUserData _).expects(user.id, *).anyNumberOfTimes().onCall { (userId: UserId, updater: UserData => UserData) =>
        Future.successful(Some((blockedUser, updater(blockedUser))))
      }

      val service = setup(user, Accepted)
      (users.updateConnectionStatus _).expects(user.id, Blocked, *, *).once().returning(Future.successful(Some(blockedUser)))
      (convs.setConversationHidden _).expects(ConvId(user.id.str), true).once().returning(Future.successful(None))
      if (federationSupported) {
        (sync.postQualifiedConnectionStatus _).expects(user.qualifiedId.get, Blocked).once().returning(Future.successful(SyncId()))
      } else {
        (sync.postConnectionStatus _).expects(user.id, Blocked).once().returning(Future.successful(SyncId()))
      }
      val combined = for {
        _         <- service.acceptConnection(user.id)
        Some(res) <- service.blockConnection(user.id)
      } yield res

      inside(result(combined)) { case resUser =>
        resUser.id shouldEqual user.id
        resUser.connection shouldEqual Blocked
      }
    }

    scenario("unblock the connection") {
      val user = UserData(UserId("user-id"), name = Name("name"), searchKey = SearchKey.simple("name"), domain = domain)
      val blockedUser = user.copy(connection = Blocked)
      (users.updateUserData _).expects(user.id, *).anyNumberOfTimes().onCall { (userId: UserId, updater: UserData => UserData) =>
        Future.successful(Some((blockedUser, updater(blockedUser))))
      }

      val service = setup(user, Accepted)
      (users.updateConnectionStatus _).expects(user.id, Blocked, *, *).once().returning(Future.successful(Some(blockedUser)))
      (convs.setConversationHidden _).expects(ConvId(user.id.str), true).once().returning(Future.successful(None))
      if (federationSupported) {
        val qId = QualifiedId(user.id, domain)
        (sync.postQualifiedConnectionStatus _).expects(qId, Blocked).once().returning(Future.successful(SyncId()))
      } else {
        (sync.postConnectionStatus _).expects(user.id, Blocked).once().returning(Future.successful(SyncId()))
      }
      (sync.syncConversations _).expects(Set(ConvId(user.id.str)), *).anyNumberOfTimes().returning(Future.successful(SyncId()))
      val combined = for {
        _    <- service.acceptConnection(user.id)
        _    <- service.blockConnection(user.id)
        conv <- service.unblockConnection(user.id)
      } yield conv

      inside(result(combined)) { case conv =>
        conv.id shouldEqual ConvId(user.id.str)
        conv.remoteId shouldEqual RConvId(user.id.str)
        conv.creator shouldEqual selfUserId
        conv.convType shouldEqual OneToOne
        conv.hidden shouldEqual false
      }
    }

    scenario("handle response after sending connect request sync job completes - ensure that remoteId of previously created conversation is updated properly") {
      val remoteId = RConvId("remote_conv")
      var otherUser = UserData("other-user").copy(domain = domain)
      val qRemoteId = RConvQualifiedId(remoteId, domain)

      val tempRemoteId = RConvId(otherUser.id.str)
      val convId = ConvId(otherUser.id.str)

      var previousConv = ConversationData(convId, tempRemoteId, convType = WaitForConnection)

      (usersStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[UserId], updater: ((UserId, Option[UserData]) => UserData)) =>
        Future.successful(keys.map {
          case id if id == otherUser.id =>
            returning(updater(id, Some(otherUser)))(otherUser = _)
          case _ => fail("Unexpected user being updated")
        }.toSet)
      }

      (users.syncUsers _).expects(Set(otherUser.id), *, *).returning(Future.successful(Option(SyncId())))
      if (federationSupported) {
        (convsStorage.getMapByQRemoteIds _).expects(Set(qRemoteId)).anyNumberOfTimes().returning(Future.successful(Map.empty))
      } else {
        (convsStorage.getMapByRemoteIds _).expects(Set(remoteId)).anyNumberOfTimes().returning(Future.successful(Map.empty))
      }
      (convsStorage.updateLocalIds _).expects(Map.empty[ConvId, ConvId]).returning(Future.successful(Set.empty))
      (convsStorage.updateOrCreateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ((ConvId, Option[ConversationData]) => ConversationData)) =>
        Future.successful(keys.map {
          case id if id == convId =>
            returning(updater(id, Some(previousConv))) { updated =>
              previousConv = updated
            }
          case _ => fail("Unexpected user being updated")
        }.toSet)
      }
      (members.addAll _).expects(Map(convId -> Map(otherUser.id -> ConversationRole.AdminRole, selfUserId -> ConversationRole.AdminRole))).returning(Future.successful({}))
      (convsStorage.updateAll2 _).expects(*, *).onCall { (keys: Iterable[ConvId], updater: ConversationData => ConversationData) =>
        if (!keys.toSet.contains(convId)) fail ("didn't try to update other conversation")
        val prev = previousConv
        Future.successful(Seq((prev, returning(updater(previousConv)) { updated =>
          previousConv = updated
        })))
      }
      (messagesStorage.getLastMessage _).expects(convId).returning(Future.successful(Some(MessageData()))) //shouldn't be needed


      val service = createBlankService()
      await {
        service.handleUserConnectionEvents(Seq(
          UserConnectionEvent(remoteId, domain, selfUserId, otherUser.id, domain, Some("Hi!"), PendingFromUser, RemoteInstant(clock.instant()), None)
        ))
      }

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
   new ConnectionServiceImpl(
     selfUserId, teamId, BackendConfigFixture.backendSignal, convs, convsStorage,
     members, messagesService, messagesStorage, users, usersStorage, sync
   )

  def initConnectionService(): ConnectionServiceImpl = {

    (usersStorage.listAll _).expects(*).anyNumberOfTimes().onCall { ids: Traversable[UserId] =>
      Future.successful(
        ids.collect {
          case id if id == otherUserId => UserData(otherUserId, name = Name("other-user"), searchKey = SearchKey.simple("other-user"))
        }.toVector
      )
    }

    if (federationSupported) {
      (convsStorage.getMapByQRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Map.empty))
    } else {
      (convsStorage.getMapByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Map.empty))
    }
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
    (usersStorage.get _).expects(*).anyNumberOfTimes().onCall{uId: UserId => Future.successful(Some(UserData.withName(uId, "")))}

    (members.addAll (_:Map[ConvId, Map[UserId, ConversationRole]])).expects(*).anyNumberOfTimes().returning(Future.successful(()))

    (messagesStorage.getLastMessage _).expects(*).anyNumberOfTimes().returns(Future.successful(None))

    (messagesService.addDeviceStartMessages _).expects(*, *).anyNumberOfTimes().onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
      Future.successful(convs.map(conv => MessageData(MessageId(), conv.id, Message.Type.STARTED_USING_DEVICE, selfUserId)).toSet)
    }
    (users.syncUsers _).expects(*, *, *).anyNumberOfTimes().returns(Future.successful(Option(SyncId())))
    new ConnectionServiceImpl(
      selfUserId, teamId, BackendConfigFixture.backendSignal, convs, convsStorage,
      members, messagesService, messagesStorage, users, usersStorage, sync
    )
  }
}
