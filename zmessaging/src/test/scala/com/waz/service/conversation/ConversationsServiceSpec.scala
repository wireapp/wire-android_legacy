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

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.Message
import com.waz.api.impl.ErrorResponse
import com.waz.content._
import com.waz.log.BasicLogging.LogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GuestRoomStateError.{GeneralError, MemberLimitReached, NotAllowed}
import com.waz.model.{ConversationData, ConversationRole, _}
import com.waz.service._
import com.waz.service.assets.{AssetService, UriHelper}
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.PushService
import com.waz.service.teams.{TeamsService, TeamsServiceImpl}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.{ConversationOverviewResponse, ConversationResponse}
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
import com.waz.zms.BuildConfig
import com.wire.signals.{CancellableFuture, EventStream, Signal, SourceSignal}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.Future

class ConversationsServiceSpec extends AndroidFreeSpec {
  import ConversationRole._

  private lazy val content        = mock[ConversationsContentUpdater]
  private lazy val messages       = mock[MessagesService]
  private lazy val msgStorage     = mock[MessagesStorage]
  private lazy val membersStorage = mock[MembersStorage]
  private lazy val userService    = mock[UserService]
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
  private lazy val folders        = mock[FoldersService]
  private lazy val network        = mock[NetworkModeService]
  private lazy val properties     = mock[PropertiesService]
  private lazy val uriHelper      = mock[UriHelper]
  private lazy val deletions      = mock[MsgDeletionStorage]
  private lazy val buttons        = mock[ButtonsStorage]
  private lazy val rolesService   = mock[ConversationRolesService]

  private lazy val globalPrefs    = new TestGlobalPreferences()
  private lazy val userPrefs      = new TestUserPreferences()
  private lazy val msgUpdater     = new MessagesContentUpdater(msgStorage, convsStorage, deletions, buttons, globalPrefs, userPrefs)

  val teamsStorage                = mock[TeamsStorage]
  val errorsService               = mock[ErrorsService]

  private val selfUserId = UserId("selfUser")
  private val convId = ConvId("conv_id1")
  private val rConvId = RConvId("r_conv_id1")

  private val domain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty

  private lazy val service = new ConversationsServiceImpl(
    None,
    selfUserId,
    domain,
    push,
    userService,
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
    convsClient,
    selectedConv,
    requests,
    assets,
    receiptStorage,
    folders,
    rolesService
  )

  private def createConvsUi(teamId: Option[TeamId] = Some(TeamId())): ConversationsUiService = {
    new ConversationsUiServiceImpl(
      selfUserId, teamId, assets, userService, messages, msgStorage,
      msgUpdater, membersStorage, content, convsStorage, network,
      service, sync, requests, convsClient, accounts, tracking, errors, uriHelper,
      properties
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
  (push.onHistoryLost _).expects().anyNumberOfTimes().returning(SourceSignal[Instant]())
  (errors.onErrorDismissed _).expects(*).anyNumberOfTimes().returning(CancellableFuture.successful(()))

  (sync.syncTeam _).expects(*).anyNumberOfTimes().returning(Future.successful(SyncId()))
  (userService.syncUsers _).expects(*, *, *).anyNumberOfTimes().returning(Future.successful(Option(SyncId())))
  (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))

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
      val selfMember = ConversationMemberData(selfUserId, convId, ConversationRole.AdminRole)

      val events = Seq(
        MemberLeaveEvent(rConvId, domain, RemoteInstant.ofEpochSec(10000), selfUserId, domain, Seq(selfUserId), reason = None)
      )
      (userService.syncIfNeeded _).expects(*, *, *, *).anyNumberOfTimes().returning(Future.successful(None))

      // check if the self is still in any conversation (they are - with self)
      (membersStorage.getByUsers _).expects(Set(selfUserId)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(selfMember))
      )
      // check if anyone is still in the conversation (no)
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq.empty)
      )
      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { _: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(convData)))
      (messages.addMemberLeaveMessage _).expects(convId, selfUserId, Set(selfUserId), *).atLeastOnce().returning(
        Future.successful(())
      )
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(convData)))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(Map.empty))

      // EXPECT
      (content.updateConversationState _).expects(where { (id, state) =>
        id.equals(convId) && state.archived.getOrElse(false)
      }).once().returning(Future.successful(None))

      // WHEN
      result(service.convStateEventProcessingStage(rConvId, events))
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

      val removerId = UserId()
      val events = Seq(
        MemberLeaveEvent(rConvId, domain, RemoteInstant.ofEpochSec(10000), removerId, domain, Seq(selfUserId), reason = None)
      )

      (userService.syncIfNeeded _).expects(*, *, *, *).anyNumberOfTimes().returning(Future.successful(None))
      (membersStorage.getByUsers _).expects(Set(selfUserId)).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(ConversationMemberData(selfUserId, convId, ConversationRole.AdminRole)))
      )
      (content.convByRemoteId _).expects(*).anyNumberOfTimes().onCall { _: RConvId =>
        Future.successful(Some(convData))
      }
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (content.setConvActive _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(convData)))
      (messages.addMemberLeaveMessage _).expects(convId, removerId, Set(selfUserId), *).atLeastOnce().returning(
        Future.successful(())
      )
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(convData)))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(Map.empty))
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(removerId))
      )

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
        MemberLeaveEvent(rConvId, domain, RemoteInstant.ofEpochSec(10000), selfUserId, domain, Seq(otherUserId), reason = None)
      )

      (userService.syncIfNeeded _).expects(Set(otherUserId), *, *, *).anyNumberOfTimes().returning(Future.successful(None))
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
      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(convData)))
      (messages.addMemberLeaveMessage _).expects(convId, selfUserId, Set(otherUserId), *).atLeastOnce().returning(
        Future.successful(())
      )
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(selfUserId))
      )
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(convData)))

      // EXPECT
      (content.updateConversationState _).expects(*, *).never()

      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }
  }

  feature("Delete conversation") {

    scenario("Delete conversation event deletes conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, domain, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId(), domain)
      )
      (messages.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (msgStorage.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).returning(Future.successful(Set.empty))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(Future.successful(Seq.empty))
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(convId, *).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(None))
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().returning(Future.successful(IndexedSeq.empty))
      (receiptStorage.removeAllForMessages _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (folders.removeConversationFromAll _).expects(convId, *).anyNumberOfTimes().returning(Future.successful(()))
      (rolesService.removeByConvId _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
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
        DeleteConversationEvent(rConvId, domain, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId(), domain)
      )
      (messages.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (msgStorage.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set[MessageId]()))
      (messages.getAssetIds _).expects(*).anyNumberOfTimes().returning(Future.successful(Set[GeneralAssetId]()))
      (assets.deleteAll _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      (receiptStorage.removeAllForMessages _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (folders.removeConversationFromAll _).expects(convId, false).anyNumberOfTimes().returning(Future.successful(()))
      (rolesService.rolesByConvId _).expects(convId).anyNumberOfTimes().returning(Signal.const(Set.empty))
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(Future.successful(Seq.empty))
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conversationData)))
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(convId, *).anyNumberOfTimes().returning(Future.successful(Set.empty))
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().returning(Future.successful(IndexedSeq.empty))
      (rolesService.removeByConvId _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      // WHEN
      result(service.convStateEventProcessingStage.apply(rConvId, events))
    }

    scenario("Delete conversation event deletes assets of the conversation from storage") {
      //GIVEN
      val conversationData = ConversationData(convId, rConvId)
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes()
        .returning(Future.successful(Some(conversationData)))

      val events = Seq(
        DeleteConversationEvent(rConvId, domain, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId(), domain)
      )

      val assetId: GeneralAssetId = AssetId()
      val messageId = MessageId()
      (messages.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set(messageId)))
      (msgStorage.findMessageIds _).expects(convId).anyNumberOfTimes().returning(Future.successful(Set(messageId)))
      (convsStorage.remove _).expects(*).anyNumberOfTimes().returning(Future.successful(()))
      (membersStorage.remove(_: ConvId, _: Iterable[UserId])).expects(*, *)
        .anyNumberOfTimes().returning(Future.successful(Set[ConversationMemberData]()))
      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        Future.successful(userIds.map(uId => ConversationMemberData(uId, convId, AdminRole)).toIndexedSeq)
      }
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().returning(Future.successful(IndexedSeq.empty))
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conversationData)))
      (msgStorage.deleteAll _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))
      (buttons.deleteAllForMessage _).expects(messageId).anyNumberOfTimes().returning(Future.successful(()))
      (receiptStorage.removeAllForMessages _).expects(Set(messageId)).anyNumberOfTimes().returning(Future.successful(()))
      (folders.removeConversationFromAll _).expects(convId, *).anyNumberOfTimes().returning(Future.successful(()))
      (rolesService.removeByConvId _).expects(convId).anyNumberOfTimes().returning(Future.successful(()))

      //EXPECT
      (messages.getAssetIds _).expects(Set(messageId)).once().returning(Future.successful(Set(assetId)))
      (assets.deleteAll _).expects(Set(assetId)).once().returning(Future.successful(()))

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
        DeleteConversationEvent(rConvId, domain, RemoteInstant.ofEpochMilli(Instant.now().toEpochMilli), UserId(), domain)
      )

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
      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(Future.successful(Some(conversationData)))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(Map.empty))


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

      (content.createConversation _).expects(*, *, ConversationType.Group, selfUserId, *, *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, *, *, *, *).once().returning(Future.successful(()))
      (sync.postConversation(_: ConvId, _: Option[UserId], _: Option[Name], _: Option[TeamId],
        _: Set[Access], _: AccessRole, _: Option[Int], _: ConversationRole))
        .expects(*, *, Some(convName), Some(teamId), *, *, *, *)
        .once()
        .returning(Future.successful(syncId))
      (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))
      (userService.findUsers _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq.empty))
      (membersStorage.isActiveMember _).expects(conv.id, *).anyNumberOfTimes().returning(Future.successful(true))
      (membersStorage.getByUsers _).expects(Set.empty[UserId]).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq.empty[ConversationMemberData])
      )

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

      val user1 = UserData("user1")
      val user2 = UserData("user2")
      val users = Set(user1, user2)

      val member1 = ConversationMemberData(user1.id, conv.id, ConversationRole.MemberRole)
      val member2 = ConversationMemberData(user2.id, conv.id, ConversationRole.MemberRole)

      (content.createConversation _).expects(*, *, ConversationType.Group, selfUserId, *, *, *, *, *, *).once().returning(Future.successful(conv))
      (messages.addConversationStartMessage _).expects(*, selfUserId, *, *, *, *).once().returning(Future.successful(()))
      (sync.postConversation(_: ConvId, _: Option[UserId], _: Option[Name], _: Option[TeamId],
        _: Set[Access], _: AccessRole, _: Option[Int], _: ConversationRole))
        .expects(*, *, Some(convName), Some(teamId), *, *, *, *)
        .once()
        .returning(Future.successful(syncId))
      (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))
      (membersStorage.getByUsers _).expects(Set(user1.id, user2.id)).once().returning(Future.successful(IndexedSeq(member1, member2)))
      (userService.findUsers _).expects(Seq(user1.id, user2.id)).anyNumberOfTimes().returning(Future.successful(Seq(Some(user1), Some(user2))))
      (membersStorage.isActiveMember _).expects(conv.id, *).anyNumberOfTimes().returning(Future.successful(true))
      (convsStorage.optSignal _).expects(conv.id).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (membersStorage.updateOrCreateAll(_: ConvId, _: Map[UserId, ConversationRole]))
        .expects(conv.id, Map(user1.id -> ConversationRole.MemberRole, user2.id -> ConversationRole.MemberRole))
        .once()
        .returning(Future.successful(Set(member1, member2)))
      (messages.addMemberJoinMessage _)
        .expects(conv.id, selfUserId, Set(user1.id, user2.id), *, *)
        .once()
        .returning(Future.successful(None))
      (sync.postConversationMemberJoin _)
        .expects(conv.id, Set(user1.id, user2.id), *)
        .once()
        .returning(Future.successful(SyncId()))

      val convsUi = createConvsUi(Some(teamId))
      val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = users.map(_.id), defaultRole = ConversationRole.MemberRole))
      data shouldEqual conv
      sId shouldEqual syncId
    }
  }

  feature("Create a group conversation with qualified users") {

    scenario("Create a group conversation with the creator and two users, both contacted") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val teamId = TeamId()
        val convName = Name("conv")
        val conv = ConversationData(team = Some(teamId), name = Some(convName))
        val syncId = SyncId()
        val domain = Domain("chala.wire.link")
        val self = UserData.withName(selfUserId, "self").copy(domain = domain)
        val user1 = UserData("user1").copy(domain = domain)
        val user2 = UserData("user2").copy(domain = domain)
        val users = Set(self, user1, user2)
        val member1 = ConversationMemberData(user1.id, conv.id, ConversationRole.MemberRole)
        val member2 = ConversationMemberData(user2.id, conv.id, ConversationRole.MemberRole)

        (content.createConversation _).expects(*, *, ConversationType.Group, selfUserId, *, *, *, *, *, *).once().returning(Future.successful(conv))
        (messages.addConversationStartMessage _).expects(*, selfUserId, *, *, *, *).once().returning(Future.successful(()))
        (sync.postConversation(_: ConvId, _: Option[UserId], _: Option[Name], _: Option[TeamId],
          _: Set[Access], _: AccessRole, _: Option[Int], _: ConversationRole))
          .expects(*, *, Some(convName), Some(teamId), *, *, *, *)
          .once()
          .returning(Future.successful(syncId))
        (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))
        (userService.findUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Seq[UserId] =>
          Future.successful {
            userIds.map { id =>
              if (id == selfUserId) Some(self)
              else if (id == user1.id) Some(user1)
              else if (id == user2.id) Some(user2)
              else None
            }
          }
        }
        (userService.isFederated(_: UserData)).expects(*).anyNumberOfTimes().onCall { user: UserData => user.id != selfUserId }
        (membersStorage.getByUsers _).expects(Set(user1.id, user2.id)).once().returning(Future.successful(IndexedSeq(member1, member2)))
        (membersStorage.isActiveMember _).expects(conv.id, *).anyNumberOfTimes().returning(Future.successful(true))
        (convsStorage.optSignal _).expects(conv.id).anyNumberOfTimes().returning(Signal.const(Some(conv)))
        (membersStorage.updateOrCreateAll(_: ConvId, _: Map[UserId, ConversationRole]))
          .expects(conv.id, Map(user1.id -> ConversationRole.MemberRole, user2.id -> ConversationRole.MemberRole))
          .once()
          .returning(Future.successful(Set(member1, member2)))
        (messages.addMemberJoinMessage _)
          .expects(conv.id, selfUserId, Set(user1.id, user2.id), *, *)
          .once()
          .returning(Future.successful(None))
        (sync.postQualifiedConversationMemberJoin _)
          .expects(conv.id, Set(user1.qualifiedId.get, user2.qualifiedId.get), *)
          .once()
          .returning(Future.successful(SyncId()))

        val convsUi = createConvsUi(Some(teamId))
        val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = Set(user1.id, user2.id), defaultRole = ConversationRole.MemberRole))
        data shouldEqual conv
        sId shouldEqual syncId
      }
    }

    scenario("Create a group conversation with the creator and two users, one uncontacted") {
      if (BuildConfig.FEDERATION_USER_DISCOVERY) {
        val teamId = TeamId()
        val convName = Name("conv")
        val conv = ConversationData(team = Some(teamId), name = Some(convName))
        val syncId = SyncId()
        val domain = Domain("chala.wire.link")
        val self = UserData.withName(selfUserId, "self").copy(domain = domain)
        val user1 = UserData("user1").copy(domain = domain)
        val user2 = UserData("user2").copy(domain = domain)

        val member1 = ConversationMemberData(user1.id, conv.id, ConversationRole.MemberRole)
        val member2 = ConversationMemberData(user2.id, conv.id, ConversationRole.MemberRole)

        (content.createConversation _).expects(*, *, ConversationType.Group, selfUserId, *, *, *, *, *, *).once().returning(Future.successful(conv))
        (messages.addConversationStartMessage _).expects(*, selfUserId, *, *, *, *).once().returning(Future.successful(()))
        (sync.postConversation(_: ConvId, _: Option[UserId], _: Option[Name], _: Option[TeamId],
          _: Set[Access], _: AccessRole, _: Option[Int], _: ConversationRole))
          .expects(*, *, Some(convName), Some(teamId), *, *, *, *)
          .once()
          .returning(Future.successful(syncId))
        (requests.await(_: SyncId)).expects(*).anyNumberOfTimes().returning(Future.successful(SyncResult.Success))
        (userService.findUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Seq[UserId] =>
          Future.successful {
            userIds.map { id =>
              if (id == selfUserId) Some(self)
              else if (id == user1.id) Some(user1)
              else if (id == user2.id) Some(user2)
              else None
            }
          }
        }
        (userService.isFederated(_: UserData)).expects(*).anyNumberOfTimes().onCall { user: UserData => user.id != selfUserId }
        (membersStorage.getByUsers _).expects(Set(user1.id, user2.id)).once().returning(Future.successful(IndexedSeq(member1)))
        (membersStorage.isActiveMember _).expects(conv.id, *).anyNumberOfTimes().returning(Future.successful(true))
        (convsStorage.optSignal _).expects(conv.id).anyNumberOfTimes().returning(Signal.const(Some(conv)))
        (membersStorage.updateOrCreateAll(_: ConvId, _: Map[UserId, ConversationRole]))
          .expects(conv.id, Map(user1.id -> ConversationRole.MemberRole, user2.id -> ConversationRole.MemberRole))
          .once()
          .returning(Future.successful(Set(member1, member2)))
        (messages.addMemberJoinMessage _)
          .expects(conv.id, selfUserId, Set(user1.id, user2.id), *, *)
          .once()
          .returning(Future.successful(None))
        (sync.postQualifiedConversationMemberJoin _)
          .expects(conv.id, Set(user1.qualifiedId.get, user2.qualifiedId.get), *)
          .once()
          .returning(Future.successful(SyncId()))

        val convsUi = createConvsUi(Some(teamId))
        val (data, sId) = result(convsUi.createGroupConversation(name = Some(convName), members = Set(user1.id, user2.id), defaultRole = ConversationRole.MemberRole))
        data shouldEqual conv
        sId shouldEqual syncId
      }
    }
  }


  feature("Update conversation") {
    scenario("Parse conversation response") {
      val creatorId = UserId("bea00721-4af0-4204-82a7-e152c9722ddc")
      val selfId = UserId("0ec303f8-b6dc-4daf-8215-e43f6be22dd8")
      val otherId = UserId("b937e85e-3611-4e29-9bda-6fe39dfd4bd0")
      val domain = "anta"
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
          |     {"status":0, "conversation_role":"${ConversationRole.AdminRole.label}", "id":"${creatorId.str}", "qualified_id": { "id": "${creatorId.str}", "domain": "$domain" } },
          |     {"status":0, "conversation_role":"${ConversationRole.MemberRole.label}", "id":"${otherId.str}", "qualified_id": { "id": "${otherId.str}", "domain": "$domain" } }
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
      response.members.get(QualifiedId(creatorId, domain)) shouldEqual Some(ConversationRole.AdminRole)
      response.members.get(QualifiedId(selfId)) shouldEqual Some(ConversationRole.AdminRole)
      response.members.get(QualifiedId(otherId, domain)) shouldEqual Some(ConversationRole.MemberRole)
    }

    scenario("updateConversationsWithDeviceStartMessage happy path") {
      val rConvId = RConvId("conv")
      val from = UserId("User1")
      val convId = ConvId(rConvId.str)
      val response = ConversationResponse(
        rConvId,
        domain,
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
        Map(QualifiedId(account1Id, domain) -> AdminRole, QualifiedId(from, domain) -> AdminRole),
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

      (userService.syncIfNeeded _).expects(*, *, *, *).returning(Future.successful(Option(SyncId())))

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

  feature("Conversation name") {
    implicit val logTag: LogTag = LogTag("ConversationServiceSpec")

    scenario("Return empty name if the conversation is not in the storage") {
      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(None))

      result(service.conversationName(convId).head) shouldEqual Name.Empty
    }

    scenario("Return the defined name if the conversation has it and is a group chat") {
      val name = Name("conversation")
      val conv = ConversationData(
        id = convId,
        name = Some(name),
        convType = ConversationType.Group
      )

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))

      result(service.conversationName(convId).head) shouldEqual name
    }

    scenario("Return the name of the other user if the conversation is 1:1") {
      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val convId = ConvId(user2.id.str)
      val conv = ConversationData(
        id = convId,
        name = None,
        convType = ConversationType.OneToOne
      )

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name)

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))

      result(service.conversationName(convId).head) shouldEqual user2.name
    }

    scenario("Return the name of the other user if the conversation is fake 1:1") {
      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val conv = ConversationData(
        id = convId,
        name = None,
        convType = ConversationType.Group
      )

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name)

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(
          ConversationMemberData(self.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user2.id, conv.id, ConversationRole.AdminRole)
        ))
      )
      (membersStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream())
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))

      result(service.conversationName(convId).head) shouldEqual user2.name
    }

    scenario("Return the defined name of the conversation even if it is fake 1:1") {
      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val name = Name("conversation")
      val conv = ConversationData(
        id = convId,
        name = Some(name),
        convType = ConversationType.Group
      )

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name)

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(
          ConversationMemberData(self.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user2.id, conv.id, ConversationRole.AdminRole)
        ))
      )
      (membersStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream())
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))

      result(service.conversationName(convId).head) shouldEqual name
    }

    scenario("Return the name generated out of usernames if the name is not defined") {
      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val user3 = UserData(name = Name("user3"))
      val conv = ConversationData(
        id = convId,
        name = None,
        convType = ConversationType.Group
      )

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name, user3.id -> user3.name)

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(
          ConversationMemberData(self.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user2.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user3.id, conv.id, ConversationRole.AdminRole)
        ))
      )
      (membersStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream())
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))

      val generatedName = Name(List(user2, user3).map(_.name).mkString(", "))
      result(service.conversationName(convId).head) shouldEqual generatedName
    }

    scenario("Return the name generated out of usernames if one of usernames is not available") {
      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val user3 = UserData(name = Name("user3"))
      val conv = ConversationData(
        id = convId,
        name = None,
        convType = ConversationType.Group
      )

      val userNames = Map(selfUserId -> self.name, user3.id -> user3.name)

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(Signal.const(Some(conv)))
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().returning(
        Future.successful(IndexedSeq(
          ConversationMemberData(self.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user2.id, conv.id, ConversationRole.AdminRole),
          ConversationMemberData(user3.id, conv.id, ConversationRole.AdminRole)
        ))
      )
      (membersStorage.onChanged _).expects().anyNumberOfTimes().returning(EventStream())
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))

      result(service.conversationName(convId).head) shouldEqual user3.name
    }

    scenario("Preserve the name after the last other user leaves the conversation") {
      import com.waz.threading.Threading.Implicits.Background

      val self = UserData(selfUserId.str)
      val user2 = UserData(name = Name("user2"))
      val user3 = UserData(name = Name("user3"))
      val convSignal = Signal(Option(ConversationData(
        id = convId,
        remoteId = rConvId,
        name = None,
        convType = ConversationType.Group
      )))

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name, user3.id -> user3.name)

      val mSelf = ConversationMemberData(self.id,  convId, ConversationRole.AdminRole)
      val m2    = ConversationMemberData(user2.id, convId, ConversationRole.AdminRole)
      val m3    = ConversationMemberData(user3.id, convId, ConversationRole.AdminRole)

      val members = Signal(IndexedSeq(mSelf, m2, m3))
      val membersOnChanged = Signal[Seq[ConversationMemberData]]()

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(convSignal)
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().onCall { _: ConvId => members.head.map(_.map(_.userId)) }
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().onCall { _: ConvId => members.head }
      (membersStorage.onChanged _).expects().anyNumberOfTimes().onCall(_ => EventStream.from(membersOnChanged))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))
      (userService.syncIfNeeded _).expects(*, *, *, *).anyNumberOfTimes().returning(Future.successful(None))
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes().returning(convSignal.head)
      (membersStorage.remove(_: ConvId, _:Iterable[UserId])).expects(convId, *).anyNumberOfTimes().onCall { (_: ConvId, userIds: Iterable[UserId]) =>
        members.head.map { ms =>
          val idSet = userIds.toSet
          val (removed, left) = ms.partition(m => idSet.contains(m.userId))
          members ! left
          membersOnChanged ! left
          removed.toSet
        }
      }

      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        members.head.map(_.filter(m => userIds.contains(m.userId)))
      }
      (usersStorage.updateAll2 _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Seq.empty))

      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(convSignal.head)
      (content.updateConversationName _).expects(convId, *).once().onCall { (_: ConvId, name: Name) =>
        convSignal.head.map {
          case Some(conv) =>
            val newConv = conv.copy(name = Some(name))
            convSignal ! Some(newConv)
            Some((conv, newConv))
          case _ => None
        }
      }
      (messages.addMemberLeaveMessage _).expects(convId, *, *, *).anyNumberOfTimes().returning(Future.successful(()))

      val service = this.service

      result(service.conversationName(convId).head) shouldEqual Name(List(user2, user3).map(_.name).mkString(", "))

      result(service.convStateEventProcessingStage.apply(rConvId, Seq(
        MemberLeaveEvent(rConvId, domain, RemoteInstant.ofEpochSec(10000), selfUserId, domain, Seq(user3.id), reason = None)
      )))
      awaitAllTasks
      result(members.head.map(_.size)) shouldEqual 2
      result(service.conversationName(convId).head) shouldEqual user2.name

      result(service.convStateEventProcessingStage.apply(rConvId, Seq(
        MemberLeaveEvent(rConvId, domain, RemoteInstant.ofEpochSec(10000), selfUserId, domain, Seq(user2.id), reason = None)
      )))
      awaitAllTasks
      result(members.head.map(_.size)) shouldEqual 1
      result(members.head.map(_.head.userId)) shouldEqual selfUserId
      result(service.conversationName(convId).head) shouldEqual user2.name
    }

    scenario("Preserve the name after the user leaves the team") {
      import com.waz.threading.Threading.Implicits.Background

      val teamId = TeamId()
      val self = UserData(selfUserId.str).copy(teamId = Some(teamId))
      val user2 = UserData(name = Name("user2")).copy(teamId = Some(teamId))
      val convSignal = Signal(Option(ConversationData(
        id = convId,
        remoteId = rConvId,
        team = Some(teamId),
        name = None,
        convType = ConversationType.Group
      )))

      val userNames = Map(selfUserId -> self.name, user2.id -> user2.name)

      val mSelf = ConversationMemberData(self.id,  convId, ConversationRole.AdminRole)
      val m2    = ConversationMemberData(user2.id, convId, ConversationRole.AdminRole)

      val members = Signal(IndexedSeq(mSelf, m2))
      val membersOnChanged = Signal[Seq[ConversationMemberData]]()

      (convsStorage.optSignal _).expects(convId).anyNumberOfTimes().returning(convSignal)
      (membersStorage.getActiveUsers _).expects(convId).anyNumberOfTimes().onCall { _: ConvId => members.head.map(_.map(_.userId)) }
      (membersStorage.getByConv _).expects(convId).anyNumberOfTimes().onCall { _: ConvId => members.head }
      (membersStorage.onChanged _).expects().anyNumberOfTimes().onCall(_ => EventStream.from(membersOnChanged))
      (userService.userNames _).expects().anyNumberOfTimes().returning(Signal.const(userNames))
      (content.convByRemoteId _).expects(rConvId).anyNumberOfTimes().returning(convSignal.head)
      (membersStorage.remove(_: ConvId, _:Iterable[UserId])).expects(convId, *).anyNumberOfTimes().onCall { (_: ConvId, userIds: Iterable[UserId]) =>
        members.head.map { ms =>
          val idSet = userIds.toSet
          val (removed, left) = ms.partition(m => idSet.contains(m.userId))
          members ! left
          membersOnChanged ! left
          removed.toSet
        }
      }

      (membersStorage.getByUsers _).expects(*).anyNumberOfTimes().onCall { userIds: Set[UserId] =>
        members.head.map(_.filter(m => userIds.contains(m.userId)))
      }
      (usersStorage.updateAll2 _).expects(*, *).anyNumberOfTimes().returning(Future.successful(Seq.empty))
      (userService.deleteUsers _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

      (convsStorage.get _).expects(convId).anyNumberOfTimes().returning(convSignal.head)
      (content.updateConversationName _).expects(convId, *).once().onCall { (_: ConvId, name: Name) =>
        convSignal.head.map {
          case Some(conv) =>
            val newConv = conv.copy(name = Some(name))
            convSignal ! Some(newConv)
            Some((conv, newConv))
          case _ => None
        }
      }
      (messages.addMemberLeaveMessage _).expects(convId, *, *, *).anyNumberOfTimes().returning(Future.successful(()))

      val service = this.service

      result(service.conversationName(convId).head) shouldEqual user2.name

      val teamsService: TeamsService =
        new TeamsServiceImpl(
          selfUserId, Some(teamId), teamsStorage, userService, usersStorage, convsStorage, membersStorage,
          content, service, sync, requests, userPrefs, errorsService, rolesService
        )

      result(teamsService.eventsProcessingStage.apply(rConvId, Seq(
        TeamEvent.MemberLeave(teamId, user2.id)
      )))

      awaitAllTasks

      result(members.head.map(_.size)) shouldEqual 1
      result(members.head.map(_.head.userId)) shouldEqual selfUserId
      result(service.conversationName(convId).head) shouldEqual user2.name
    }
  }

  feature("Join Guestroom Conversation") {

    scenario("Parse conversation overview response") {
      val rConvId = RConvId("remote-conv-id")
      val convName = "Test Squad Meeting"
      val jsonStr =
        s"""
           |{
           | "id": "${rConvId.str}",
           | "name": "$convName"
           |}
        """.stripMargin

      val jsonObject = new JSONObject(jsonStr)
      val response: ConversationOverviewResponse = ConversationOverviewResponse.Decoder(jsonObject)

      response.id shouldBe rConvId
      response.name shouldBe convName
    }
  }

  scenario("Get guestroom info for already joined conversation") {
    val key = "join_key"
    val code = "join_code"
    val convName = "Services Squad Conv"
    val response = ConversationOverviewResponse(rConvId, convName)

    (convsClient.getGuestroomOverview _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Right(response)))

    val conversationData = ConversationData()
    (convsStorage.getByRemoteId _)
      .expects(rConvId)
      .anyNumberOfTimes()
      .returning(Future.successful(Some(conversationData)))

    val convInfo = result(service.getGuestroomInfo(key, code))

    convInfo shouldBe Right(GuestRoomInfo.ExistingConversation(conversationData))
  }

  scenario("Get guestroom info for new conversation") {
    val key = "join_key"
    val code = "join_code"
    val convName = "Services Squad Conv"
    val response = ConversationOverviewResponse(rConvId, convName)

    (convsClient.getGuestroomOverview _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Right(response)))

    (convsStorage.getByRemoteId _)
      .expects(rConvId)
      .anyNumberOfTimes()
      .returning(Future.successful(None))

    val convInfo = result(service.getGuestroomInfo(key, code))

    convInfo shouldBe Right(GuestRoomInfo.Overview(convName))
  }

  scenario("Get guestroom info returns NotAllowed when client returns no-conversation-code") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse(404, "error", "no-conversation-code")

    (convsClient.getGuestroomOverview _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    (convsStorage.getByRemoteId _)
      .expects(rConvId)
      .never()

    val convInfo = result(service.getGuestroomInfo(key, code))

    convInfo shouldBe Left(NotAllowed)
  }

  scenario("Get guestroom info returns MemberLimitReached when client returns too-many-members") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse(404, "error", "too-many-members")

    (convsClient.getGuestroomOverview _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    (convsStorage.getByRemoteId _)
      .expects(rConvId)
      .never()

    val convInfo = result(service.getGuestroomInfo(key, code))

    convInfo shouldBe Left(MemberLimitReached)
  }

  scenario("Get guestroom info returns GeneralError when client returns another error") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse.InternalError

    (convsClient.getGuestroomOverview _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    (convsStorage.getByRemoteId _)
      .expects(rConvId)
      .never()

    val convInfo = result(service.getGuestroomInfo(key, code))

    convInfo shouldBe Left(GeneralError)
  }

  scenario("Join conversation returns None when client returns None") {
    val key = "join_key"
    val code = "join_code"

    (convsClient.postJoinConversation _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Right(None)))

    val convInfo = result(service.joinConversation(key, code))

    convInfo shouldBe Right(None)
  }

  scenario("Join conversation returns NotAllowed when client returns no-conversation-code") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse(404, "error", "no-conversation-code")

    (convsClient.postJoinConversation _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    val convInfo = result(service.joinConversation(key, code))

    convInfo shouldBe Left(NotAllowed)
  }

  scenario("Join conversation returns MemberLimitReached when client returns too-many-members") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse(404, "error", "too-many-members")

    (convsClient.postJoinConversation _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    val convInfo = result(service.joinConversation(key, code))

    convInfo shouldBe Left(MemberLimitReached)
  }

  scenario("Join conversation returns GeneralError when client returns another error") {
    val key = "join_key"
    val code = "join_code"
    val error = ErrorResponse.InternalError

    (convsClient.postJoinConversation _)
      .expects(key, code)
      .anyNumberOfTimes()
      .returning(CancellableFuture.successful(Left(error)))

    val convInfo = result(service.joinConversation(key, code))

    convInfo shouldBe Left(GeneralError)
  }

}
