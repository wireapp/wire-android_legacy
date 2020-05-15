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
import com.waz.model.{ConversationData, ConversationRole, _}
import com.waz.service._
import com.waz.service.assets.AssetService
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.{NotificationService, PushService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.{BgEventSource, EventStream, Signal, SourceSignal}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConversationServiceSpec extends AndroidFreeSpec {
  import ConversationRole._

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
  private lazy val buttons        = mock[ButtonsStorage]
  private lazy val rolesService   = mock[ConversationRolesService]

  private lazy val globalPrefs    = new TestGlobalPreferences()
  private lazy val userPrefs      = new TestUserPreferences()
  private lazy val msgUpdater     = new MessagesContentUpdater(msgStorage, convsStorage, deletions, buttons, globalPrefs)

  private val selfUserId = UserId("user1")
  private val convId = ConvId("conv_id1")
  private val rConvId = RConvId("r_conv_id1")

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
    folders,
    rolesService
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

      (membersStorage.getByUsers _).expects(Set(selfUserId)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(ConversationMemberData(selfUserId, convId, ConversationRole.AdminRole)))
      )
      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { _: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      // EXPECT
      (content.updateConversationState _).expects(where { (id, state) =>
        id.equals(convId) && state.archived.getOrElse(false)
      }).once().returning(Future.successful(None))

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

      (membersStorage.getByUsers _).expects(Set(selfUserId)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(ConversationMemberData(selfUserId, convId, ConversationRole.AdminRole)))
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

      val otherUserId = UserId()
      val events = Seq(
        MemberLeaveEvent(rConvId, RemoteInstant.ofEpochSec(10000), selfUserId, Seq(otherUserId))
      )

      (membersStorage.getByUsers _).expects(Set(otherUserId)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(ConversationMemberData(otherUserId, convId, ConversationRole.MemberRole)))
      )
      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { id: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))
      (messages.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))

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
      (messages.getAssetIds _).expects(*).returning(Future.successful(Set.empty))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(convId).once().returning(Future.successful(()))
      (membersStorage.getActiveUsers _).expects(convId).once().returning(Future.successful(Seq.empty))

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
      (msgStorage.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).returning(Future.successful(Set.empty))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.getActiveUsers _).expects(convId).once().returning(Future.successful(Seq.empty))

      //EXPECT
      (convsStorage.remove _).expects(convId).once().returning(Future.successful(()))

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
      (msgStorage.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      (receiptStorage.removeAllForMessages _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (folders.removeConversationFromAll _).expects(convId, false).anyNumberOfTimes().returning(Future.successful(()))
      (rolesService.rolesByConvId _).expects(convId).anyNumberOfTimes().returning(Signal.const(Set.empty))
      (membersStorage.getActiveUsers _).expects(convId).once().returning(Future.successful(Seq.empty))

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
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        Future.successful(userIds.map(uId => ConversationMemberData(uId, convId, AdminRole)).toIndexedSeq)
      }

      //EXPECT
      (messages.getAssetIds _).expects(Set(messageId)).once().returning(Future.successful(Set(assetId)))
      (assets.deleteAll _).expects(Set(assetId)).once().returning(Future.successful(()))
      (membersStorage.getActiveUsers _).expects(convId).once().returning(Future.successful(Seq.empty))

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
      (messages.getAssetIds _).expects(Set(messageId)).anyNumberOfTimes()
        .returning(Future.successful(Set[GeneralAssetId]()))

      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.delete _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).once().returning(Future.successful(()))
      (folders.removeConversationFromAll _).expects(convId, false).once().returning(Future.successful(()))
      (rolesService.removeByConvId _).expects(convId).once().returning(Future.successful(()))
      (messages.findMessageIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set(messageId)))
      (msgStorage.findMessageIds _).expects(convId).atLeastOnce().returning(Future.successful(Set(messageId)))
      (buttons.deleteAllForMessage _).expects(messageId).atLeastOnce().returning(Future.successful(()))
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        Future.successful(userIds.map(uId => ConversationMemberData(uId, convId, AdminRole)).toIndexedSeq)
      }
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(Future.successful(Seq.empty))

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

      (content.createConversationWithMembers _).expects(*, *, ConversationType.Group, selfUserId, Set.empty[UserId], *, *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, Set.empty[UserId], *, *, *).once().returning(Future.successful(()))
      (sync.postConversation _).expects(*, Set.empty[UserId], Some(convName), Some(teamId), *, *, *, *).once().returning(Future.successful(syncId))

      val convsUi = createConvsUi(Some(teamId))
      val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), defaultRole = ConversationRole.MemberRole))
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

      (content.createConversationWithMembers _).expects(*, *, ConversationType.Group, selfUserId, users.map(_.id), *, *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, users.map(_.id), *, *, *).once().returning(Future.successful(()))
      (sync.postConversation _).expects(*, users.map(_.id), Some(convName), Some(teamId), *, *, *, *).once().returning(Future.successful(syncId))

      val convsUi = createConvsUi(Some(teamId))
      val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = users.map(_.id), defaultRole = ConversationRole.MemberRole))
      data shouldEqual conv
      sId shouldEqual syncId
    }
  }

  feature("Update conversation") {
    scenario("Parse conversation response") {
      val creatorId = UserId("bea00721-4af0-4204-82a7-e152c9722ddc")
      val selfId = UserId("0ec303f8-b6dc-4daf-8215-e43f6be22dd8")
      val otherId = UserId("b937e85e-3611-4e29-9bda-6fe39dfd4bd0")
      val jsonStr =
        s"""
          |{"access":["invite","code"],
          | "creator":"${creatorId.str}",
          | "access_role":"non_activated",
          | "members":{
          |   "self":{
          |     "hidden_ref":null,
          |     "status":0,
          |     "service":null,
          |     "otr_muted_ref":null,
          |     "conversation_role":"wire_admin",
          |     "status_time":"1970-01-01T00:00:00.000Z",
          |     "hidden":false,
          |     "status_ref":"0.0",
          |     "id":"${selfId.str}",
          |     "otr_archived":false,
          |     "otr_muted_status":null,
          |     "otr_muted":false,
          |     "otr_archived_ref":null
          |   },
          |   "others":[
          |     {"status":0, "conversation_role":"${ConversationRole.AdminRole.label}", "id":"${creatorId.str}"},
          |     {"status":0, "conversation_role":"${ConversationRole.MemberRole.label}", "id":"${otherId.str}"}
          |   ]
          | },
          | "name":"www",
          | "team":"cda744e7-742c-46ee-bc0e-a0da23d77f00",
          | "id":"23ffe1e8-721d-4dea-9b76-2cd215f9e874",
          | "type":0,
          | "receipt_mode":1,
          | "last_event_time":
          | "1970-01-01T00:00:00.000Z",
          | "message_timer":null,
          | "last_event":"0.0"
          |}
        """.stripMargin

      val jsonObject = new JSONObject(jsonStr)
      val response: ConversationResponse = ConversationResponse.Decoder(jsonObject)

      response.creator shouldEqual creatorId
      response.members.size shouldEqual 3
      response.members.get(creatorId) shouldEqual Some(ConversationRole.AdminRole)
      response.members.get(selfId) shouldEqual Some(ConversationRole.AdminRole)
      response.members.get(otherId) shouldEqual Some(ConversationRole.MemberRole)
    }

    scenario("updateConversationsWithDeviceStartMessage happy path") {

      val rConvId = RConvId("conv")
      val from = UserId("User1")
      val convId = ConvId(rConvId.str)
      val response = ConversationResponse(
        rConvId,
        Some(Name("conv")),
        from,
        ConversationType.Group,
        None,
        MuteSet.AllAllowed,
        RemoteInstant.Epoch,
        archived = false,
        RemoteInstant.Epoch,
        Set.empty,
        None,
        None,
        None,
        Map(account1Id -> AdminRole, from -> AdminRole),
        None
      )

      (convsStorage.apply[Seq[(ConvId, ConversationResponse)]] _).expects(*).onCall { x: (Map[ConvId, ConversationData] => Seq[(ConvId, ConversationResponse)]) =>
        Future.successful(x(Map[ConvId, ConversationData]()))
      }

      (convsStorage.updateOrCreateAll _).expects(*).onCall { x: Map[ConvId, Option[ConversationData] => ConversationData ] =>
        Future.successful(x.values.map(_(None)).toSet)
      }

      (content.convsByRemoteId _).expects(*).returning(Future.successful(Map()))

      (membersStorage.setAll _).expects(*).returning(Future.successful(()))
      (membersStorage.getActiveUsers2 _).expects(*).anyNumberOfTimes().onCall { convIds: Set[ConvId] =>
        Future.successful(if (convIds.contains(convId)) Map(convId -> Set(account1Id, from)) else Map.empty[ConvId, Set[UserId]])
      }
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        Future.successful(userIds.map(uId => ConversationMemberData(uId, convId, AdminRole)).toIndexedSeq)
      }

      (users.syncIfNeeded _).expects(*, *).returning(Future.successful(Option(SyncId())))

      (messages.addDeviceStartMessages _).expects(*, *).onCall{ (convs: Seq[ConversationData], selfUserId: UserId) =>
        convs.headOption.flatMap(_.name) should be (Some(Name("conv")))
        convs.headOption.map(_.muted) should be (Some(MuteSet.AllAllowed))
        convs.headOption.map(_.creator) should be (Some(from))
        convs.headOption.map(_.remoteId) should be (Some(rConvId))
        convs.headOption.map(_.id) should be (Some(convId))
        Future.successful(Set(MessageData(MessageId(), convId, Message.Type.STARTED_USING_DEVICE, selfUserId, time = RemoteInstant.Epoch)))
      }

      result(service.updateConversationsWithDeviceStartMessage(Seq(response), Map.empty))
    }
  }

}
