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

import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, _}
import com.waz.service._
import com.waz.service.assets2.AssetService
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.{NotificationService, PushService}
import com.waz.service.tracking.TrackingService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{BgEventSource, EventStream, Signal, SourceSignal}
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConversationServiceSpec extends AndroidFreeSpec {

  lazy val convosUpdaterMock  = mock[ConversationsContentUpdater]
  lazy val messagesMock       = mock[MessagesService]
  lazy val msgStorageMock     = mock[MessagesStorage]
  lazy val membersMock        = mock[MembersStorage]
  lazy val usersMock          = mock[UserService]
  lazy val syncMock           = mock[SyncServiceHandle]
  lazy val pushMock           = mock[PushService]
  lazy val usersStorageMock   = mock[UsersStorage]
  lazy val convoStorageMock   = mock[ConversationStorage]
  lazy val convoContentMock   = mock[ConversationsContentUpdater]
  lazy val syncHandleMock     = mock[SyncServiceHandle]
  lazy val errorMock          = mock[ErrorsService]
  lazy val messageUpdaterMock = mock[MessagesContentUpdater]
  lazy val userPrefsMock      = mock[UserPreferences]
  lazy val syncRequestMock    = mock[SyncRequestService]
  lazy val eventSchedulerMock = mock[EventScheduler]
  lazy val trackingMock       = mock[TrackingService]
  lazy val convosClientMock   = mock[ConversationsClient]
  lazy val selectedConvoMock  = mock[SelectedConversationService]
  lazy val assetServiceMock   = mock[AssetService]
  lazy val receiptStorageMock = mock[ReadReceiptsStorage]
  lazy val notificationServiceMock = mock[NotificationService]

  val selfUserId = UserId("user1")
  val convId = ConvId("conv_id1")
  val rConvId = RConvId("r_conv_id1")
  val convsInStorage = Signal[Map[ConvId, ConversationData]]()

  lazy val service = new ConversationsServiceImpl(
    None,
    selfUserId,
    pushMock,
    usersMock,
    usersStorageMock,
    membersMock,
    convoStorageMock,
    convoContentMock,
    syncHandleMock,
    errorMock,
    messagesMock,
    messageUpdaterMock,
    userPrefsMock,
    eventSchedulerMock,
    trackingMock,
    convosClientMock,
    selectedConvoMock,
    syncRequestMock,
    assetServiceMock,
    receiptStorageMock,
    notificationServiceMock
  )

  // mock mapping from remote to local conversation ID
  (convoStorageMock.getByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq(convId)))

  // EXPECTS
  (usersStorageMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (usersStorageMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (convoStorageMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (convoStorageMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersMock.onDeleted _).expects().anyNumberOfTimes().returning(EventStream())
  (selectedConvoMock.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.empty)
  (pushMock.onHistoryLost _).expects().anyNumberOfTimes().returning(new SourceSignal[Instant] with BgEventSource)
  (errorMock.onErrorDismissed _).expects(*).anyNumberOfTimes().returning(CancellableFuture.successful(()))


  feature("Archive conversation") {

    scenario("Archive conversation when the user leaves it remotely") {

      // GIVEN
      val convData = ConversationData(
        convId,
        rConvId,
        Some(Name("name")),
        UserId(),
        ConversationType.Group,
        lastEventTime = RemoteInstant.Epoch,
        archived = false,
        muted = MuteSet.AllMuted
      )

      val events = Seq(
        MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), selfUserId, Seq(selfUserId))
      )

      (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersMock.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (convoContentMock.updateConversationState _).expects(where { (id, state) =>
        id.equals(convId) && state.archived.getOrElse(false)
      }).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Does not archive conversation when the user is removed by someone else") {

      // GIVEN
      val convData = ConversationData(
        convId,
        rConvId,
        Some(Name("name")),
        UserId(),
        ConversationType.Group,
        lastEventTime = RemoteInstant.Epoch,
        archived = false,
        muted = MuteSet.AllMuted
      )

      val events = Seq(
        MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), UserId(), Seq(selfUserId))
      )

      (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersMock.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (convoContentMock.updateConversationState _).expects(*, *).never()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Does not archive conversation when the user is not the one being removed") {

      // GIVEN
      val convData = ConversationData(
        convId,
        rConvId,
        Some(Name("name")),
        UserId(),
        ConversationType.Group,
        lastEventTime = RemoteInstant.Epoch,
        archived = false,
        muted = MuteSet.AllMuted
      )

      val events = Seq(
        MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), selfUserId, Seq(UserId()))
      )

      (convoContentMock.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersMock.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (convoContentMock.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (convoContentMock.updateConversationState _).expects(*, *).never()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }
  }

  feature("Delete conversation") {

    scenario("Delete conversation event shows notification") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (convoContentMock.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val dummyUserId = UserId()
      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), dummyUserId)
      )

      // EXPECT
      (notificationServiceMock.displayNotificationForDeletingConversation _).expects(*, conversationData)
        .once().returning(Future.successful(()))

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (convoContentMock.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notificationServiceMock.displayNotificationForDeletingConversation _).expects(*, *).anyNumberOfTimes()
        .returning(Future.successful(()))
      (messagesMock.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messagesMock.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assetServiceMock.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))

      //EXPECT
      (convoStorageMock.remove _).expects(convId).once().returning(Future.successful(()))
      (membersMock.delete _).expects(convId).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes messages of the conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (convoContentMock.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notificationServiceMock.displayNotificationForDeletingConversation _).expects(*, *).anyNumberOfTimes()
        .returning(Future.successful(()))
      (messagesMock.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messagesMock.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assetServiceMock.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convoStorageMock.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersMock.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))

      //EXPECT
      (messageUpdaterMock.deleteMessagesForConversation _).expects(convId).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes assets of the conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (convoContentMock.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notificationServiceMock.displayNotificationForDeletingConversation _).expects(*, *).anyNumberOfTimes()
        .returning(Future.successful(()))

      val assetId: GeneralAssetId = AssetId()
      val messageId = MessageId()
      val assetMessage = MessageData(id = messageId, convId = convId, assetId = Some(assetId))
      (messagesMock.findMessageIds _).expects(convId).anyNumberOfTimes()
        .returning(Future.successful(Set(messageId)))

      //EXPECT
      (messagesMock.getAssetIds _).expects(Set(messageId)).once().returning(Future.successful(Set(assetId)))
      (assetServiceMock.deleteAll _).expects(Set(assetId)).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes read receipts of the conversation from storage") {
      //GIVEN
      val readReceiptsOn = 1
      val conversationData = ConversationData(convId, rConvId, receiptMode = Some(readReceiptsOn))
      (convoContentMock.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notificationServiceMock.displayNotificationForDeletingConversation _).expects(*, *).anyNumberOfTimes()
        .returning(Future.successful(()))


      val messageId = MessageId()
      val message = MessageData(id = messageId, convId = convId)
      (messagesMock.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set(messageId)))
      (messagesMock.getAssetIds _).expects(Set(messageId)).anyNumberOfTimes()
        .returning(Future.successful(Set[GeneralAssetId]()))

      (assetServiceMock.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convoStorageMock.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersMock.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (messageUpdaterMock.deleteMessagesForConversation _).expects(*).anyNumberOfTimes()
        .returning(Future.successful(()))

      //EXPECT
      (receiptStorageMock.removeAllForMessages _).expects(Set(messageId)).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    //TODO: add: scenario("If the user is at the conversation screen at the time of deletion, current conv. is cleared")

  }

}
