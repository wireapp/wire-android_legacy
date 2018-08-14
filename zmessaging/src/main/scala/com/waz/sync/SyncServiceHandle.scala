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
package com.waz.sync

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.{ShouldSyncConversations, ShouldSyncInitial}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.ClientId
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync._
import com.waz.model.{AccentColor, Availability, _}
import com.waz.service._
import com.waz.sync.queue.ConvLock
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.concurrent.duration._

trait SyncServiceHandle {
  def syncSearchQuery(query: SearchQuery): Future[SyncId]
  def exactMatchHandle(handle: Handle): Future[SyncId]
  def syncUsers(ids: Set[UserId]): Future[SyncId]
  def syncSelfUser(): Future[SyncId]
  def deleteAccount(): Future[SyncId]
  def syncConversations(ids: Set[ConvId] = Set.empty, dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncConvLink(id: ConvId): Future[SyncId]
  def syncTeam(dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncTeamMember(id: UserId): Future[SyncId]
  def syncConnectedUsers(): Future[SyncId]
  def syncConnections(dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority): Future[SyncId]
  def postAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncId]
  def postRemoveBot(cId: ConvId, botId: UserId): Future[SyncId]

  def postSelfUser(info: UserInfo): Future[SyncId]
  def postSelfPicture(picture: Option[AssetId]): Future[SyncId]
  def postSelfName(name: String): Future[SyncId]
  def postSelfAccentColor(color: AccentColor): Future[SyncId]
  def postAvailability(status: Availability): Future[SyncId]
  def postMessage(id: MessageId, conv: ConvId, editTime: RemoteInstant): Future[SyncId]
  def postDeleted(conv: ConvId, msg: MessageId): Future[SyncId]
  def postRecalled(conv: ConvId, currentMsgId: MessageId, recalledMsgId: MessageId): Future[SyncId]
  def postAssetStatus(id: MessageId, conv: ConvId, exp: Option[FiniteDuration], status: AssetStatus.Syncable): Future[SyncId]
  def postLiking(id: ConvId, liking: Liking): Future[SyncId]
  def postConnection(user: UserId, name: String, message: String): Future[SyncId]
  def postConnectionStatus(user: UserId, status: ConnectionStatus): Future[SyncId]
  def postConversationName(id: ConvId, name: String): Future[SyncId]
  def postConversationMemberJoin(id: ConvId, members: Seq[UserId]): Future[SyncId]
  def postConversationMemberLeave(id: ConvId, member: UserId): Future[SyncId]
  def postConversationState(id: ConvId, state: ConversationState): Future[SyncId]
  def postConversation(id: ConvId, users: Set[UserId], name: Option[String], team: Option[TeamId], access: Set[Access], accessRole: AccessRole): Future[SyncId]
  def postLastRead(id: ConvId, time: RemoteInstant): Future[SyncId]
  def postCleared(id: ConvId, time: RemoteInstant): Future[SyncId]
  def postAddressBook(ab: AddressBook): Future[SyncId]
  def postTypingState(id: ConvId, typing: Boolean): Future[SyncId]
  def postOpenGraphData(conv: ConvId, msg: MessageId, editTime: RemoteInstant): Future[SyncId]
  def postReceipt(conv: ConvId, message: MessageId, user: UserId, tpe: ReceiptType): Future[SyncId]

  def registerPush(token: PushToken): Future[SyncId]
  def deletePushToken(token: PushToken): Future[SyncId]

  def syncSelfClients(): Future[SyncId]
  def syncSelfPermissions(): Future[SyncId]
  def postClientLabel(id: ClientId, label: String): Future[SyncId]
  def syncClients(user: UserId): Future[SyncId]
  def syncClientsLocation(): Future[SyncId]

  def syncPreKeys(user: UserId, clients: Set[ClientId]): Future[SyncId]
  def postSessionReset(conv: ConvId, user: UserId, client: ClientId): Future[SyncId]

  def performFullSync(): Future[Unit]
}

class AndroidSyncServiceHandle(service: SyncRequestService, timeouts: Timeouts, userPreferences: UserPreferences) extends SyncServiceHandle {

  import Threading.Implicits.Background
  import com.waz.model.sync.SyncRequest._

  val shouldSyncAll           = userPreferences(ShouldSyncInitial)
  val shouldSyncConversations = userPreferences(ShouldSyncConversations)

  for {
    all   <- shouldSyncAll()
    convs <- shouldSyncConversations()
    _     <-
      if (all) performFullSync()
      else if (convs) syncConversations()
      else Future.successful({})
    _ <- shouldSyncAll := false
    _ <- shouldSyncConversations := false
  } yield {}

  private def addRequest(req: SyncRequest, priority: Int = Priority.Normal, dependsOn: Seq[SyncId] = Nil, optional: Boolean = false, timeout: Long = 0, forceRetry: Boolean = false, delay: FiniteDuration = Duration.Zero): Future[SyncId] = {
    val timestamp = SyncJob.timestamp
    val startTime = if (delay == Duration.Zero) 0 else timestamp + delay.toMillis
    service.addRequest(SyncJob(SyncId(), req, dependsOn.toSet, priority = priority, optional = optional, timeout = timeout, timestamp = timestamp, startTime = startTime), forceRetry)
  }

  def syncSearchQuery(query: SearchQuery) = addRequest(SyncSearchQuery(query), priority = Priority.High)
  def syncUsers(ids: Set[UserId]) = addRequest(SyncUser(ids))
  def exactMatchHandle(handle: Handle) = addRequest(ExactMatchHandle(handle), priority = Priority.High)
  def syncSelfUser() = addRequest(SyncSelf, priority = Priority.High)
  def deleteAccount() = addRequest(DeleteAccount)
  def syncConversations(ids: Set[ConvId], dependsOn: Option[SyncId]) =
    if (ids.nonEmpty) addRequest(SyncConversation(ids), priority = Priority.Normal, dependsOn = dependsOn.toSeq)
    else              addRequest(SyncConversations,     priority = Priority.High,   dependsOn = dependsOn.toSeq)

  def syncConvLink(id: ConvId) = addRequest(SyncConvLink(id))
  def syncTeam(dependsOn: Option[SyncId] = None): Future[SyncId] = addRequest(SyncTeam, priority = Priority.High, dependsOn = dependsOn.toSeq)
  def syncTeamMember(id: UserId): Future[SyncId] = addRequest(SyncTeamMember(id))
  def syncConnectedUsers() = addRequest(SyncConnectedUsers)
  def syncConnections(dependsOn: Option[SyncId]) = addRequest(SyncConnections, dependsOn = dependsOn.toSeq)
  def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority) = addRequest(SyncRichMedia(id), priority = priority)

  def postSelfUser(info: UserInfo) = addRequest(PostSelf(info))
  def postSelfPicture(picture: Option[AssetId]) = addRequest(PostSelfPicture(picture))
  def postSelfName(name: String) = addRequest(PostSelfName(name))
  def postSelfAccentColor(color: AccentColor) = addRequest(PostSelfAccentColor(color))
  def postAvailability(status: Availability) = addRequest(PostAvailability(status))
  def postMessage(id: MessageId, conv: ConvId, time: RemoteInstant) = addRequest(PostMessage(conv, id, time), timeout = System.currentTimeMillis() + timeouts.messages.sendingTimeout.toMillis, forceRetry = true)
  def postDeleted(conv: ConvId, msg: MessageId) = addRequest(PostDeleted(conv, msg))
  def postRecalled(conv: ConvId, msg: MessageId, recalled: MessageId) = addRequest(PostRecalled(conv, msg, recalled))
  def postAssetStatus(id: MessageId, conv: ConvId, exp: Option[FiniteDuration], status: AssetStatus.Syncable) = addRequest(PostAssetStatus(conv, id, exp, status))
  def postAddressBook(ab: AddressBook) = addRequest(PostAddressBook(ab))
  def postConnection(user: UserId, name: String, message: String) = addRequest(PostConnection(user, name, message))
  def postConnectionStatus(user: UserId, status: ConnectionStatus) = addRequest(PostConnectionStatus(user, Some(status)))
  def postTypingState(conv: ConvId, typing: Boolean) = addRequest(PostTypingState(conv, typing), optional = true, timeout = System.currentTimeMillis() + timeouts.typing.refreshDelay.toMillis)
  def postConversationName(id: ConvId, name: String) = addRequest(PostConvName(id, name))
  def postConversationState(id: ConvId, state: ConversationState) = addRequest(PostConvState(id, state))
  def postConversationMemberJoin(id: ConvId, members: Seq[UserId]) = addRequest(PostConvJoin(id, members.toSet))
  def postConversationMemberLeave(id: ConvId, member: UserId) = addRequest(PostConvLeave(id, member))
  def postConversation(id: ConvId, users: Set[UserId], name: Option[String], team: Option[TeamId], access: Set[Access], accessRole: AccessRole) = addRequest(PostConv(id, users, name, team, access, accessRole))
  def postLiking(id: ConvId, liking: Liking): Future[SyncId] = addRequest(PostLiking(id, liking))
  def postLastRead(id: ConvId, time: RemoteInstant) = addRequest(PostLastRead(id, time), priority = Priority.Low, delay = timeouts.messages.lastReadPostDelay)
  def postCleared(id: ConvId, time: RemoteInstant) = addRequest(PostCleared(id, time))
  def postOpenGraphData(conv: ConvId, msg: MessageId, time: RemoteInstant) = addRequest(PostOpenGraphMeta(conv, msg, time), priority = Priority.Low)
  def postReceipt(conv: ConvId, message: MessageId, user: UserId, tpe: ReceiptType): Future[SyncId] = addRequest(PostReceipt(conv, message, user, tpe), priority = Priority.Optional)
  def postAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId) = addRequest(PostAddBot(cId, pId, iId))
  def postRemoveBot(cId: ConvId, botId: UserId) = addRequest(PostRemoveBot(cId, botId))

  def registerPush(token: PushToken) = addRequest(RegisterPushToken(token), priority = Priority.High, forceRetry = true)
  def deletePushToken(token: PushToken) = addRequest(DeletePushToken(token), priority = Priority.Low)

  def syncSelfClients() = addRequest(SyncSelfClients, priority = Priority.Critical)
  def syncSelfPermissions() = addRequest(SyncSelfPermissions, priority = Priority.High)
  def postClientLabel(id: ClientId, label: String) = addRequest(PostClientLabel(id, label))
  def syncClients(user: UserId) = addRequest(SyncClients(user))
  def syncClientsLocation() = addRequest(SyncClientsLocation)
  def syncPreKeys(user: UserId, clients: Set[ClientId]) = addRequest(SyncPreKeys(user, clients))

  def postSessionReset(conv: ConvId, user: UserId, client: ClientId) = addRequest(PostSessionReset(conv, user, client))

  override def performFullSync(): Future[Unit] = for {
    id1 <- syncSelfUser()
    id6 <- syncConnections()
    id2 <- syncSelfClients()
    id3 <- syncSelfPermissions()
    id4 <- syncTeam()
    id5 <- syncConversations()
    _ <- service.scheduler.await(Set(id1, id2, id3, id4, id5, id6))
  } yield ()
}

trait SyncHandler {
  def apply(req: SyncRequest): Future[SyncResult]
  def apply(req: SerialExecutionWithinConversation, lock: ConvLock): Future[SyncResult]
}

class AccountSyncHandler(zms: ZMessaging) extends SyncHandler {
  import com.waz.model.sync.SyncRequest._

  override def apply(req: SyncRequest): Future[SyncResult] = req match {
    case SyncSelfClients                        => zms.otrClientsSync.syncSelfClients()
    case SyncClients(user)                      => zms.otrClientsSync.syncClients(user)
    case SyncClientsLocation                    => zms.otrClientsSync.syncClientsLocation()
    case SyncPreKeys(user, clients)             => zms.otrClientsSync.syncPreKeys(Map(user -> clients.toSeq))
    case PostClientLabel(id, label)             => zms.otrClientsSync.postLabel(id, label)
    case SyncConversation(convs)                => zms.conversationSync.syncConversations(convs.toSeq)
    case SyncConversations                      => zms.conversationSync.syncConversations()
    case SyncConvLink(conv)                     => zms.conversationSync.syncConvLink(conv)
    case SyncUser(u)                            => zms.usersSync.syncUsers(u.toSeq: _*)
    case SyncSearchQuery(query)                 => zms.usersearchSync.syncSearchQuery(query)
    case ExactMatchHandle(query)                => zms.usersearchSync.exactMatchHandle(query)
    case SyncRichMedia(messageId)               => zms.richmediaSync.syncRichMedia(messageId)
    case DeletePushToken(token)                 => zms.gcmSync.deleteGcmToken(token)
    case PostConnection(userId, name, message)  => zms.connectionsSync.postConnection(userId, name, message)
    case PostConnectionStatus(userId, status)   => zms.connectionsSync.postConnectionStatus(userId, status)
    case SyncTeam                               => zms.teamsSync.syncTeam()
    case SyncTeamMember(userId)                 => zms.teamsSync.syncMember(userId)
    case SyncConnectedUsers                     => zms.usersSync.syncConnectedUsers()
    case SyncConnections                        => zms.connectionsSync.syncConnections()
    case SyncSelf                               => zms.usersSync.syncSelfUser()
    case SyncSelfPermissions                    => zms.teamsSync.syncSelfPermissions()
    case DeleteAccount                          => zms.usersSync.deleteAccount()
    case PostSelf(info)                         => zms.usersSync.postSelfUser(info)
    case PostSelfPicture(_)                     => zms.usersSync.postSelfPicture()
    case PostSelfName(name)                     => zms.usersSync.postSelfName(name)
    case PostSelfAccentColor(color)             => zms.usersSync.postSelfAccentColor(color)
    case PostAvailability(availability)         => zms.usersSync.postAvailability(availability)
    case PostAddressBook(ab)                    => zms.addressbookSync.postAddressBook(ab)
    case RegisterPushToken(token)               => zms.gcmSync.registerPushToken(token)
    case PostLiking(convId, liking)             => zms.reactionsSync.postReaction(convId, liking)
    case PostAddBot(cId, pId, iId)              => zms.integrationsSync.addBot(cId, pId, iId)
    case PostRemoveBot(cId, botId)              => zms.integrationsSync.removeBot(cId, botId)
    case PostDeleted(convId, msgId)             => zms.messagesSync.postDeleted(convId, msgId)
    case PostLastRead(convId, time)             => zms.lastReadSync.postLastRead(convId, time)
    case PostOpenGraphMeta(conv, msg, time)     => zms.openGraphSync.postMessageMeta(conv, msg, time)
    case PostRecalled(convId, msg, recall)      => zms.messagesSync.postRecalled(convId, msg, recall)
    case PostSessionReset(conv, user, client)   => zms.otrSync.postSessionReset(conv, user, client)
    case PostReceipt(conv, msg, user, tpe)      => zms.messagesSync.postReceipt(conv, msg, user, tpe)
    case req: SerialExecutionWithinConversation => throw new IllegalArgumentException(s"trying to run $req without conv lock")
    case Unknown                                => Future successful SyncResult.Success
  }

  override def apply(req: SerialExecutionWithinConversation, lock: ConvLock): Future[SyncResult] = {
    implicit val convLock = lock
    req match {
      case PostMessage(convId, messageId, time)                => zms.messagesSync.postMessage(convId, messageId, time)
      case PostAssetStatus(cid, mid, exp, status)              => zms.messagesSync.postAssetStatus(cid, mid, exp, status)
      case PostConvJoin(convId, u)                             => zms.conversationSync.postConversationMemberJoin(convId, u)
      case PostConvLeave(convId, u)                            => zms.conversationSync.postConversationMemberLeave(convId, u)
      case PostConv(convId, u, name, team, access, accessRole) => zms.conversationSync.postConversation(convId, u, name, team, access, accessRole)
      case PostConvName(convId, name)                          => zms.conversationSync.postConversationName(convId, name)
      case PostConvState(convId, state)                        => zms.conversationSync.postConversationState(convId, state)
      case PostTypingState(convId, ts)                         => zms.typingSync.postTypingState(convId, ts)
      case PostCleared(convId, time)                           => zms.clearedSync.postCleared(convId, time)
    }
  }
}
