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
package com.waz.testutils

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.ClientId
import com.waz.model.sync.ReceiptType
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.{AccentColor, Availability, _}
import com.waz.sync._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class EmptySyncService extends EmptySyncServiceTrait

object EmptySyncService extends EmptySyncService

trait EmptySyncServiceTrait extends SyncServiceHandle {
  override def syncSearchQuery(query: SearchQuery) = sid
  override def exactMatchHandle(handle: Handle) = sid
  override def syncConversations(ids: Set[ConvId], dependsOn: Option[SyncId] = None) = sid
  override def syncConvLink(id: ConvId) = sid
  override def syncTeam(dependsOn: Option[SyncId] = None) = sid
  override def syncTeamMember(id: UserId) = sid
  override def syncSelfUser() = sid
  override def deleteAccount() = sid
  override def syncUsers(ids: Set[UserId]) = sid
  override def syncConnectedUsers() = sid
  override def syncConnections(dependsOn: Option[SyncId] = None) = sid
  override def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority): Future[SyncId] = sid

  override def postConnection(user: UserId, name: String, message: String) = sid
  override def postConnectionStatus(userId: UserId, status: ConnectionStatus) = sid
  override def postSelfUser(u: UserInfo) = sid
  override def postMessage(id: MessageId, conv: ConvId, time: RemoteInstant) = sid
  override def postAssetStatus(id: MessageId, conv: ConvId, exp: Option[FiniteDuration], status: AssetStatus.Syncable) = sid
  override def postDeleted(conv: ConvId, msg: MessageId): Future[SyncId] = sid
  override def postRecalled(conv: ConvId, msg: MessageId, recalled: MessageId): Future[SyncId] = sid
  override def postSelfPicture(picture: Option[AssetId]) = sid
  override def postSelfName(name: String) = sid
  override def postSelfAccentColor(color: AccentColor) = sid
  override def postAvailability(status: Availability) = sid
  override def postConversationName(id: ConvId, n: String) = sid
  override def postConversationMemberJoin(id: ConvId, members: Seq[UserId]) = sid
  override def postConversationMemberLeave(id: ConvId, member: UserId) = sid
  override def postConversationState(id: ConvId, s: ConversationState) = sid
  override def postConversation(id: ConvId, users: Set[UserId], name: Option[String], team: Option[TeamId], access: Set[Access], accessRole: AccessRole) = sid
  override def postLastRead(id: ConvId, time: RemoteInstant) = sid
  override def postCleared(id: ConvId, time: RemoteInstant): Future[SyncId] = sid
  override def postAddressBook(ab: AddressBook) = sid
  override def postTypingState(id: ConvId, t: Boolean) = sid
  override def postOpenGraphData(conv: ConvId, msg: MessageId, time: RemoteInstant) = sid
  override def postReceipt(conv: ConvId, message: MessageId, user: UserId, tpe: ReceiptType) = sid
  override def postAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId) = sid
  override def postRemoveBot(cId: ConvId, botId: UserId) = sid

  override def registerPush(token: PushToken) = sid
  override def deletePushToken(token: PushToken) = sid

  override def syncSelfClients(): Future[SyncId] = sid
  override def syncSelfPermissions(): Future[SyncId] = sid
  override def postLiking(id: ConvId, liking: Liking): Future[SyncId] = sid
  override def postClientLabel(id: ClientId, label: String): Future[SyncId] = sid
  override def syncClients(user: UserId): Future[SyncId] = sid
  override def syncClientsLocation(): Future[SyncId] = sid
  override def syncPreKeys(user: UserId, clients: Set[ClientId]): Future[SyncId] = sid
  override def postSessionReset(conv: ConvId, user: UserId, client: ClientId): Future[SyncId] = sid

  override def performFullSync(): Future[Unit] = Future.successful({})

  private def sid = Future.successful(SyncId())

}
