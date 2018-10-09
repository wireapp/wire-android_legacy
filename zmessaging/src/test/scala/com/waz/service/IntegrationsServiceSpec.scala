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
package com.waz.service

import com.waz.content.{AssetsStorage, ConversationStorage, MembersStorage, UsersStorage}
import com.waz.model._
import com.waz.service.conversation.ConversationsUiService
import com.waz.service.messages.MessagesService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.IntegrationsClient
import com.waz.sync.queue.SyncScheduler
import com.waz.sync.{SyncRequestService, SyncResult, SyncServiceHandle}
import com.waz.threading.{CancellableFuture, Threading}

import scala.concurrent.Future

class IntegrationsServiceSpec extends AndroidFreeSpec {

  implicit val ctx = Threading.Background

  val sync = mock[SyncServiceHandle]
  val client = mock[IntegrationsClient]

  val teamId = TeamId()

  val syncScheduler = mock[SyncScheduler]
  val srs           = mock[SyncRequestService]
  val assets        = mock[AssetsStorage]
  val users         = mock[UsersStorage]
  val members       = mock[MembersStorage]
  val messages      = mock[MessagesService]
  val convsUi       = mock[ConversationsUiService]
  val convs         = mock[ConversationStorage]


  lazy val service = new IntegrationsServiceImpl(account1Id, Some(teamId), client, assets, sync, users, members, messages, convs, convsUi, srs)

  scenario("Previously downloaded AssetData are not recreated if found in database") {
    val asset1 = AssetData(id = AssetId("asset-1"), remoteId = Some(RAssetId("asset-1")))
    val asset1Copy = AssetData(id = AssetId("asset-1-copy"), remoteId = Some(RAssetId("asset-1")))
    val asset2 = AssetData(id = AssetId("asset-2"), remoteId = Some(RAssetId("asset-2")))

    val service1 = IntegrationData(id = IntegrationId("service-1"), provider = ProviderId(""), asset = Some(asset1Copy.id))
    val service2 = IntegrationData(id = IntegrationId("service-2"), provider = ProviderId(""), asset = Some(asset2.id))

    val beResponse = Map(
      service1 -> Some(asset1Copy), //get some integration a second time from the backend
      service2 -> Some(asset2)
    )

    val fromDatabase = Set(asset1)

    (client.searchTeamIntegrations _).expects(None, *).returning(CancellableFuture.successful(Right(beResponse)))

    (assets.findByRemoteIds _).expects(Set(asset1Copy.remoteId.get, asset2.remoteId.get)).returning(Future.successful(fromDatabase))
    (assets.insertAll _).expects(Set(asset2)).returning(Future.successful(Set()))

    val res = result(service.searchIntegrations())

    res.right.get.find(_.id == service1.id).get shouldEqual service1.copy(asset = Some(asset1.id))
    res.right.get.find(_.id == service2.id).get shouldEqual service2.copy(asset = Some(asset2.id))
  }

  scenario("create new conversation with service if none exists") {

    val pId = ProviderId("provider-id")
    val serviceId = IntegrationId("service-id")

    val createConvSyncId = SyncId("create-conv")
    val addedBotSyncId = SyncId("added-bot")

    val createdConv = ConversationData(ConvId("created-conv-id"))
    val serviceUserId = UserId("service-user")

    (users.findUsersForService _).expects(serviceId).returning(Future.successful(Set.empty))
    (convsUi.createGroupConversation _).expects(Option.empty[String], Set.empty[UserId], false).returning(Future.successful(createdConv, createConvSyncId))
    (srs.scheduler _).expects().anyNumberOfTimes().returning(syncScheduler)
    (syncScheduler.await (_: SyncId)).expects(*).twice().returning(Future.successful(SyncResult.Success))
    (sync.postAddBot _).expects(createdConv.id, pId, serviceId).returning(Future.successful(addedBotSyncId))
    (members.getActiveUsers _).expects(createdConv.id).returning(Future.successful(Seq(account1Id, serviceUserId)))
    (messages.addConnectRequestMessage _).expects(createdConv.id, account1Id, serviceUserId, "", "", true).returning(Future.successful(null))

    result(service.getOrCreateConvWithService(pId, serviceId)) shouldEqual Right(createdConv.id)
  }

  scenario("Opening conversation with service does not return group conversations in which that service is located, but creates a new conv") {

    val pId = ProviderId("provider-id")
    val serviceId = IntegrationId("service-id")

    val serviceUserInGroupId = UserId("service-user-in-group")

    val serviceUserInGroup = UserData(serviceUserInGroupId, name = "service", searchKey = SearchKey.simple("service"), providerId = Some(pId), integrationId = Some(serviceId))
    val groupConvId = ConvId("group-conv")

    val membersInGroupConv = Set(
      ConversationMemberData(account1Id, groupConvId),
      ConversationMemberData(serviceUserInGroupId, groupConvId),
      ConversationMemberData(UserId("some other user"), groupConvId)
    )

    val createConvSyncId = SyncId("create-conv")
    val addedBotSyncId = SyncId("added-bot")

    val createdConv = ConversationData(ConvId("created-conv-id"))
    val serviceUserId = UserId("service-user")

    (users.findUsersForService _).expects(serviceId).returning(Future.successful(Set(serviceUserInGroup)))
    (members.getByUsers _).expects(Set(serviceUserInGroupId)).returning(Future.successful(membersInGroupConv.filter(_.userId == serviceUserInGroupId).toIndexedSeq))
    (members.getByConvs _).expects(Set(groupConvId)).returning(Future.successful(membersInGroupConv.toIndexedSeq))
    (convs.getAll _).expects(*).onCall { (ids: Traversable[ConvId]) =>
      if (ids.nonEmpty) fail("Should be no matching conversations")
      Future.successful(Seq.empty)
    }

    (convsUi.createGroupConversation _).expects(Option.empty[String], Set.empty[UserId], false).returning(Future.successful(createdConv, createConvSyncId))
    (srs.scheduler _).expects().anyNumberOfTimes().returning(syncScheduler)
    (syncScheduler.await (_: SyncId)).expects(*).twice().returning(Future.successful(SyncResult.Success))
    (sync.postAddBot _).expects(createdConv.id, pId, serviceId).returning(Future.successful(addedBotSyncId))
    (members.getActiveUsers _).expects(createdConv.id).returning(Future.successful(Seq(account1Id, serviceUserId)))
    (messages.addConnectRequestMessage _).expects(createdConv.id, account1Id, serviceUserId, "", "", true).returning(Future.successful(null))

    result(service.getOrCreateConvWithService(pId, serviceId)) shouldEqual Right(createdConv.id)

  }

  scenario("Open previous conversation with service if one exists") {

    val pId = ProviderId("provider-id")
    val serviceId = IntegrationId("service-id")

    val serviceUserId = UserId("service-user-in-group")

    val serviceUser = UserData(serviceUserId, name = "service", searchKey = SearchKey.simple("service"), providerId = Some(pId), integrationId = Some(serviceId))
    val existingConvId = ConvId("existing-conv")
    val existingConv = ConversationData(existingConvId, team = Some(teamId), name = None)

    val membersInGroupConv = Set(
      ConversationMemberData(account1Id, existingConvId),
      ConversationMemberData(serviceUserId, existingConvId)
    )

    (users.findUsersForService _).expects(serviceId).returning(Future.successful(Set(serviceUser)))
    (members.getByUsers _).expects(Set(serviceUserId)).returning(Future.successful(membersInGroupConv.filter(_.userId == serviceUserId).toIndexedSeq))
    (members.getByConvs _).expects(Set(existingConvId)).returning(Future.successful(membersInGroupConv.toIndexedSeq))
    (convs.getAll _).expects(*).onCall { (ids: Traversable[ConvId]) =>
      if (ids.isEmpty) fail("Should be 1 matching conversations")
      Future.successful(Seq(Some(existingConv)))
    }

    result(service.getOrCreateConvWithService(pId, serviceId)) shouldEqual Right(existingConv.id)

  }

}