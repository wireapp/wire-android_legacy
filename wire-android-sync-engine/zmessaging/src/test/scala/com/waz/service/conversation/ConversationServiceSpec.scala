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

import com.waz.api.Message
import com.waz.content._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConversationData, _}
import com.waz.service._
import com.waz.service.assets2.AssetService
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.{NotificationService, PushService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{BgEventSource, EventStream, Signal, SourceSignal}
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConversationServiceSpec extends AndroidFreeSpec {

  private lazy val content        = mock[ConversationsContentUpdater]
  private lazy val messages       = mock[MessagesService]
  private lazy val msgStorage     = mock[MessagesStorage]
  private lazy val membersStorage = mock[MembersStorage]
  private lazy val users          = mock[UserService]
  private lazy val sync           = mock[SyncServiceHandle]
  private lazy val push           = mock[PushService]
  private lazy val usersStorage   = mock[UsersStorage]
  private lazy val convsStorage   = mock[ConversationStorage]
  private lazy val errors         = mock[ErrorsService]
  private lazy val requests       = mock[SyncRequestService]
  private lazy val eventScheduler = mock[EventScheduler]
  private lazy val convsClient    = mock[ConversationsClient]
  private lazy val selectedConv   = mock[SelectedConversationService]
  private lazy val assets         = mock[AssetService]
  private lazy val receiptStorage = mock[ReadReceiptsStorage]
  private lazy val notifications  = mock[NotificationService]
  private lazy val folders        = mock[FoldersService]
  private lazy val network        = mock[NetworkModeService]
  private lazy val properties     = mock[PropertiesService]
  private lazy val deletions      = mock[MsgDeletionStorage]

  private lazy val globalPrefs    = new TestGlobalPreferences()
  private lazy val userPrefs      = new TestUserPreferences()
  private lazy val msgUpdater     = new MessagesContentUpdater(msgStorage, convsStorage, deletions, globalPrefs)

  private val selfUserId = UserId("user1")
  private val convId = ConvId("conv_id1")
  private val rConvId = RConvId("r_conv_id1")
  private val convsInStorage = Signal[Map[ConvId, ConversationData]]()

  private lazy val service = new ConversationsServiceImpl(
    None,
    selfUserId,
    push,
    users,
    usersStorage,
    membersStorage,
    convsStorage,
    content,
    sync,
    errors,
    messages,
    msgUpdater,
    userPrefs,
    eventScheduler,
    tracking,
    convsClient,
    selectedConv,
    requests,
    assets,
    receiptStorage,
    notifications,
    folders
  )

  private def createConvsUi(teamId: Option[TeamId] = Some(TeamId())): ConversationsUiService = {
    new ConversationsUiServiceImpl(
      selfUserId, teamId, assets, usersStorage, messages, msgStorage,
      msgUpdater, membersStorage, content, convsStorage, network,
      service, sync, convsClient, accounts, tracking, errors, properties
    )
  }

  // mock mapping from remote to local conversation ID
  (convsStorage.getByRemoteIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq(convId)))

  // EXPECTS
  (usersStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (usersStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (convsStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (convsStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream())
  (membersStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream())
  (membersStorage.onDeleted _).expects().anyNumberOfTimes().returning(EventStream())
  (selectedConv.selectedConversationId _).expects().anyNumberOfTimes().returning(Signal.const(None))
  (push.onHistoryLost _).expects().anyNumberOfTimes().returning(new SourceSignal[Instant] with BgEventSource)
  (errors.onErrorDismissed _).expects(*).anyNumberOfTimes().returning(CancellableFuture.successful(()))


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

      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (content.updateConversationState _).expects(where { (id, state) =>
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

      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (content.updateConversationState _).expects(*, *).never()

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

      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (content.updateConversationState _).expects(*, *).never()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }
  }

  feature("Delete conversation") {

    scenario("Delete conversation event shows notification") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))
      (messages.findMessageIds _).expects(convId).once().returning(Future.successful(Set.empty))

      val dummyUserId = UserId()
      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), dummyUserId)
      )

      // EXPECT
      (notifications.displayNotificationForDeletingConversation _).expects(*, *, conversationData)
        .once().returning(Future.successful(()))

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notifications.displayNotificationForDeletingConversation _).expects(*, *, *).anyNumberOfTimes()
        .returning(Future.successful(()))
      (messages.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))

      //EXPECT
      (convsStorage.remove _).expects(convId).once().returning(Future.successful(()))
      (membersStorage.delete _).expects(convId).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes messages of the conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notifications.displayNotificationForDeletingConversation _).expects(*, *, *).anyNumberOfTimes()
        .returning(Future.successful(()))
      (messages.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes assets of the conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notifications.displayNotificationForDeletingConversation _).expects(*, *, *).anyNumberOfTimes()
        .returning(Future.successful(()))

      val assetId: GeneralAssetId = AssetId()
      val messageId = MessageId()
      (messages.findMessageIds _).expects(convId).anyNumberOfTimes()
        .returning(Future.successful(Set(messageId)))

      //EXPECT
      (messages.getAssetIds _).expects(Set(messageId)).once().returning(Future.successful(Set(assetId)))
      (assets.deleteAll _).expects(Set(assetId)).once()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes read receipts of the conversation from storage") {
      //GIVEN
      val readReceiptsOn = 1
      val conversationData = ConversationData(convId, rConvId, receiptMode = Some(readReceiptsOn))
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId())
      )
      (notifications.displayNotificationForDeletingConversation _).expects(*, *, *).anyNumberOfTimes()
        .returning(Future.successful(()))

      val messageId = MessageId()
      (messages.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set(messageId)))
      (messages.getAssetIds _).expects(Set(messageId)).anyNumberOfTimes()
        .returning(Future.successful(Set[GeneralAssetId]()))

      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).once().returning(Future.successful(()))

      //EXPECT
      (receiptStorage.removeAllForMessages _).expects(Set(messageId)).once().returning(Future.successful(()))

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    //TODO: add: scenario("If the user is at the conversation screen at the time of deletion, current conv. is cleared")

  }

  feature("Create a group conversation") {
    scenario("Create empty group conversation") {
      val teamId = TeamId()
      val convName = Name("conv")
      val conv = ConversationData(team = Some(teamId), name = Some(convName))
      val syncId = SyncId()

      (content.createConversationWithMembers _).expects(*, *, ConversationType.Group, selfUserId, Set.empty[UserId], *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, Set.empty[UserId], *, *, *).once().returning(Future.successful(()))
      (sync.postConversation _).expects(*, Set.empty[UserId], Some(convName), Some(teamId), *, *, *).once().returning(Future.successful(syncId))

      val convsUi = createConvsUi(Some(teamId))
      val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = Set.empty))
      data shouldEqual conv
      sId shouldEqual syncId
    }

    scenario("Create a group conversation with the creator and two users") {
      val teamId = TeamId()
      val convName = Name("conv")
      val conv = ConversationData(team = Some(teamId), name = Some(convName))
      val syncId = SyncId()
      val self = UserData(selfUserId.str)
      val user1 = UserData("user1")
      val user2 = UserData("user2")
      val users = Set(self, user1, user2)

      (content.createConversationWithMembers _).expects(*, *, ConversationType.Group, selfUserId, users.map(_.id), *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, users.map(_.id), *, *, *).once().returning(Future.successful(()))
      (sync.postConversation _).expects(*, users.map(_.id), Some(convName), Some(teamId), *, *, *).once().returning(Future.successful(syncId))

      val convsUi = createConvsUi(Some(teamId))
      val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = users.map(_.id)))
      data shouldEqual conv
      sId shouldEqual syncId
    }
  }

  feature("Update conversation") {
    scenario("updateConversationsWithDeviceStartMessage happy path") {

      val rConvId = RConvId("conv")
      val from = UserId("User1")
      val convId = ConvId(rConvId.str)
      val response = ConversationResponse(
        rConvId, Some(Name("conv")), from, ConversationType.Group, None, MuteSet.AllAllowed, RemoteInstant.Epoch, archived = false, RemoteInstant.Epoch, Set.empty, None, None, None, Set(account1Id, from), None
      )

      (convsStorage.apply[Seq[(ConvId, ConversationResponse)]] _).expects(*).onCall { x: (Map[ConvId, ConversationData] => Seq[(ConvId, ConversationResponse)]) =>
        Future.successful(x(Map[ConvId, ConversationData]()))
      }

      (convsStorage.updateOrCreateAll _).expects(*).onCall { x: Map[ConvId, Option[ConversationData] => ConversationData ] =>
        Future.successful(x.values.map(_(None)).toSet)
      }

      (content.convsByRemoteId _).expects(*).returning(Future.successful(Map()))

      (membersStorage.setAll _).expects(*).returning(Future.successful(()))

      (users.syncIfNeeded _).expects(*, *).returning(Future.successful(Option(SyncId())))

      (messages.addDeviceStartMessages _).expects(*, *).onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
        convs.headOption.flatMap(_.name) should be (Some(Name("conv")))
        convs.headOption.map(_.muted) should be (Some(MuteSet.AllAllowed))
        convs.headOption.map(_.creator) should be (Some(from))
        convs.headOption.map(_.remoteId) should be (Some(rConvId))
        convs.headOption.map(_.id) should be (Some(convId))
        Future.successful(Set(MessageData(MessageId(), convId, Message.Type.STARTED_USING_DEVICE, selfUserId, time = RemoteInstant.Epoch)))
      }

      result(service.updateConversationsWithDeviceStartMessage(Seq(response)))
    }
  }


}
