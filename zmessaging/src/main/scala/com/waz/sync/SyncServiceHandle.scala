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
import com.waz.api.NetworkMode
import com.waz.content.UserPreferences.{SelfClient, ShouldSyncConversations, shouldSyncAllOnUpdate}
import com.waz.content.{UserPreferences, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.{ClientId, OtrClientIdMap, QOtrClientIdMap}
import com.waz.model.sync.SyncJob.Priority
import com.waz.model.sync._
import com.waz.model.{AccentColor, Availability, _}
import com.waz.service.AccountManager.ClientRegistrationState.Registered
import com.waz.service._
import com.waz.service.assets.UploadAssetStatus
import com.waz.sync.SyncResult.Failure
import com.waz.threading.Threading
import com.wire.signals.Signal
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

trait SyncServiceHandle {
  def syncSearchResults(ids: Set[UserId]): Future[SyncId]
  def syncQualifiedSearchResults(qIds: Set[QualifiedId]): Future[SyncId]
  def syncSearchQuery(query: SearchQuery): Future[SyncId]
  def syncUsers(ids: Set[UserId]): Future[SyncId]
  def syncQualifiedUsers(qIds: Set[QualifiedId]): Future[SyncId]
  def syncSelfUser(): Future[SyncId]
  def deleteAccount(): Future[SyncId]
  def syncConversations(ids: Set[ConvId] = Set.empty, dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncConvLink(id: ConvId): Future[SyncId]
  def syncTeam(dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncTeamData(): Future[SyncId]
  def syncTeamMember(id: UserId): Future[SyncId]
  def syncConnections(dependsOn: Option[SyncId] = None): Future[SyncId]
  def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority): Future[SyncId]
  def syncFolders(): Future[SyncId]
  def syncLegalHoldRequest(): Future[SyncId]
  def syncClientsForLegalHold(convId: RConvId): Future[SyncId]

  def postAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId): Future[SyncId]
  def postRemoveBot(cId: ConvId, botId: UserId): Future[SyncId]

  def postSelfUser(info: UserInfo): Future[SyncId]
  def postSelfPicture(picture: UploadAssetId): Future[SyncId]
  def postSelfName(name: Name): Future[SyncId]
  def postSelfAccentColor(color: AccentColor): Future[SyncId]
  def postAvailability(status: Availability): Future[SyncId]
  def postMessage(id: MessageId, conv: ConvId, editTime: RemoteInstant): Future[SyncId]
  def postDeleted(conv: ConvId, msg: MessageId): Future[SyncId]
  def postRecalled(conv: ConvId, currentMsgId: MessageId, recalledMsgId: MessageId): Future[SyncId]
  def postAssetStatus(id: MessageId, conv: ConvId, exp: Option[FiniteDuration], status: UploadAssetStatus): Future[SyncId]
  def postLiking(id: ConvId, liking: Liking): Future[SyncId]
  def postConnection(user: UserId, name: Name, message: String): Future[SyncId]
  def postQualifiedConnection(qId: QualifiedId): Future[SyncId]
  def postConnectionStatus(user: UserId, status: ConnectionStatus): Future[SyncId]
  def postQualifiedConnectionStatus(qId: QualifiedId, status: ConnectionStatus): Future[SyncId]
  def postReceiptMode(id: ConvId, receiptMode: Int): Future[SyncId]
  def postConversationName(id: ConvId, name: Name): Future[SyncId]
  def postConversationMemberJoin(id: ConvId, members: Set[UserId], defaultRole: ConversationRole): Future[SyncId]
  def postQualifiedConversationMemberJoin(id: ConvId, members: Set[QualifiedId], defaultRole: ConversationRole): Future[SyncId]
  def postConversationMemberLeave(id: ConvId, member: UserId): Future[SyncId]
  def postConversationMemberLeave(id: ConvId, member: QualifiedId): Future[SyncId]
  def postConversationState(id: ConvId, state: ConversationState): Future[SyncId]
  def postConversation(id:          ConvId,
                       otherUser:   Option[UserId],
                       name:        Option[Name],
                       team:        Option[TeamId],
                       access:      Set[Access],
                       accessRole:  AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole
                      ): Future[SyncId]
  def postConversation(id:          ConvId,
                       otherUser:   QualifiedId,
                       team:        Option[TeamId],
                       access:      Set[Access],
                       accessRole:  AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole): Future[SyncId]
  def postConversationRole(id: ConvId, member: UserId, newRole: ConversationRole, origRole: ConversationRole): Future[SyncId]
  def postLastRead(id: ConvId, time: RemoteInstant): Future[SyncId]
  def postCleared(id: ConvId, time: RemoteInstant): Future[SyncId]
  def postTypingState(id: ConvId, typing: Boolean): Future[SyncId]
  def postOpenGraphData(conv: ConvId, msg: MessageId, editTime: RemoteInstant): Future[SyncId]
  def postReceipt(conv: ConvId, messages: Seq[MessageId], user: UserId, tpe: ReceiptType): Future[SyncId]
  def postProperty(key: PropertyKey, value: Boolean): Future[SyncId]
  def postProperty(key: PropertyKey, value: Int): Future[SyncId]
  def postProperty(key: PropertyKey, value: String): Future[SyncId]
  def postFolders(): Future[SyncId]
  def postButtonAction(messageId: MessageId, buttonId: ButtonId, senderId: UserId): Future[SyncId]
  def postTrackingId(trackingId: TrackingId): Future[SyncId]

  def registerPush(token: PushToken): Future[SyncId]
  def deletePushToken(token: PushToken): Future[SyncId]

  def syncSelfClients(): Future[SyncId]
  def syncSelfPermissions(): Future[SyncId]
  def postClientLabel(id: ClientId, label: String): Future[SyncId]
  def postClientCapabilities(): Future[SyncId]
  def syncClients(users: Set[QualifiedId]): Future[SyncId]
  def syncProperties(): Future[SyncId]

  def syncPreKeys(qId: QualifiedId, clientIds: Set[ClientId]): Future[SyncId]
  def postSessionReset(conv: ConvId, user: UserId, client: ClientId): Future[SyncId]
  def postSessionReset(conv: ConvId, user: QualifiedId, client: ClientId): Future[SyncId]

  def performFullSync(): Future[Unit]

  def deleteGroupConversation(teamId: TeamId, rConvId: RConvId): Future[SyncId]
}

class AndroidSyncServiceHandle(account:         UserId,
                               service:         SyncRequestService,
                               timeouts:        Timeouts,
                               userPreferences: UserPreferences,
                               usersStorage:    UsersStorage) extends SyncServiceHandle with DerivedLogTag {
  import Threading.Implicits.Background
  import com.waz.model.sync.SyncRequest._

  private val shouldSyncAll           = userPreferences(shouldSyncAllOnUpdate)
  private val shouldSyncConversations = userPreferences(ShouldSyncConversations)
  private val isRegistered = userPreferences(SelfClient).signal.map {
    case Registered(_) => true
    case _ => false
  }

  Signal.zip(isRegistered, shouldSyncAll.signal, shouldSyncConversations.signal).foreach {
    case (true, true, _) =>
      performFullSync().flatMap(_ => shouldSyncAll := false).flatMap(_ => shouldSyncConversations := false)
    case (true, false, true) =>
      syncConversations().flatMap(_ => shouldSyncConversations := false)
    case _ =>
  }

  private def addRequest(req: SyncRequest, priority: Int = Priority.Normal, dependsOn: Seq[SyncId] = Nil, forceRetry: Boolean = false, delay: FiniteDuration = Duration.Zero): Future[SyncId] =
    service.addRequest(account, req, priority, dependsOn, forceRetry, delay)

  def syncSearchResults(users: Set[UserId]) = addRequest(SyncSearchResults(users))
  def syncQualifiedSearchResults(qIds: Set[QualifiedId]) = addRequest(SyncQualifiedSearchResults(qIds))
  def syncSearchQuery(query: SearchQuery) = addRequest(SyncSearchQuery(query), priority = Priority.High)
  def syncUsers(ids: Set[UserId]): Future[SyncId] = addRequest(SyncUser(ids))
  def syncQualifiedUsers(qIds: Set[QualifiedId]): Future[SyncId] = addRequest(SyncQualifiedUsers(qIds))
  def syncSelfUser() = addRequest(SyncSelf, priority = Priority.High)
  def deleteAccount() = addRequest(DeleteAccount)
  def syncConversations(ids: Set[ConvId], dependsOn: Option[SyncId]) =
    if (ids.nonEmpty) addRequest(SyncConversation(ids), priority = Priority.Normal, dependsOn = dependsOn.toSeq)
    else              addRequest(SyncConversations,     priority = Priority.High,   dependsOn = dependsOn.toSeq)
  def syncConvLink(id: ConvId) = addRequest(SyncConvLink(id))
  def syncTeam(dependsOn: Option[SyncId] = None): Future[SyncId] = addRequest(SyncTeam, priority = Priority.High, dependsOn = dependsOn.toSeq)
  def syncTeamData(): Future[SyncId] = addRequest(SyncTeamData)
  def syncTeamMember(id: UserId): Future[SyncId] = addRequest(SyncTeamMember(id))
  def syncConnections(dependsOn: Option[SyncId]) = addRequest(SyncConnections, dependsOn = dependsOn.toSeq)
  def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority) = addRequest(SyncRichMedia(id), priority = priority)
  def syncFolders(): Future[SyncId] = addRequest(SyncFolders)
  def syncLegalHoldRequest(): Future[SyncId] = addRequest(SyncLegalHoldRequest)
  def syncClientsForLegalHold(convId: RConvId): Future[SyncId] = addRequest(SyncClientsForLegalHold(convId))

  def postSelfUser(info: UserInfo) = addRequest(PostSelf(info))
  def postSelfPicture(picture: UploadAssetId) = addRequest(PostSelfPicture(picture))
  def postSelfName(name: Name) = addRequest(PostSelfName(name))
  def postSelfAccentColor(color: AccentColor) = addRequest(PostSelfAccentColor(color))
  def postAvailability(status: Availability) = addRequest(PostAvailability(status))
  def postMessage(id: MessageId, conv: ConvId, time: RemoteInstant) = addRequest(PostMessage(conv, id, time), forceRetry = true)
  def postDeleted(conv: ConvId, msg: MessageId) = addRequest(PostDeleted(conv, msg))
  def postRecalled(conv: ConvId, msg: MessageId, recalled: MessageId) = addRequest(PostRecalled(conv, msg, recalled))
  def postAssetStatus(id: MessageId, conv: ConvId, exp: Option[FiniteDuration], status: UploadAssetStatus) = addRequest(PostAssetStatus(conv, id, exp, status))
  def postConnection(user: UserId, name: Name, message: String): Future[SyncId] = addRequest(PostConnection(user, name, message))
  def postQualifiedConnection(qId: QualifiedId): Future[SyncId] = addRequest(PostQualifiedConnection(qId))
  def postConnectionStatus(user: UserId, status: ConnectionStatus) = addRequest(PostConnectionStatus(user, Some(status)))
  def postQualifiedConnectionStatus(qId: QualifiedId, status: ConnectionStatus) = addRequest(PostQualifiedConnectionStatus(qId, Some(status)))
  def postTypingState(conv: ConvId, typing: Boolean) = addRequest(PostTypingState(conv, typing))
  def postConversationName(id: ConvId, name: Name) = addRequest(PostConvName(id, name))
  def postConversationState(id: ConvId, state: ConversationState) = addRequest(PostConvState(id, state))
  def postConversationMemberJoin(id: ConvId, members: Set[UserId], defaultRole: ConversationRole): Future[SyncId] =
    addRequest(PostConvJoin(id, members, defaultRole))
  def postQualifiedConversationMemberJoin(id: ConvId, members: Set[QualifiedId], defaultRole: ConversationRole): Future[SyncId] =
    addRequest(PostQualifiedConvJoin(id, members, defaultRole))
  def postConversationMemberLeave(id: ConvId, member: UserId): Future[SyncId] = addRequest(PostConvLeave(id, member))
  def postConversationMemberLeave(id: ConvId, member: QualifiedId): Future[SyncId] = addRequest(PostQualifiedConvLeave(id, member))
  def postConversation(id: ConvId,
                       otherUser: Option[UserId],
                       name: Option[Name],
                       team: Option[TeamId],
                       access: Set[Access],
                       accessRole: AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole): Future[SyncId] =
    addRequest(PostConv(id, otherUser, name, team, access, accessRole, receiptMode, defaultRole))
  def postConversation(id: ConvId,
                       otherUser: QualifiedId,
                       team: Option[TeamId],
                       access: Set[Access],
                       accessRole: AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole): Future[SyncId] =
    addRequest(PostQualified1To1Conv(id, otherUser, team, access, accessRole, receiptMode, defaultRole))
  def postReceiptMode(id: ConvId, receiptMode: Int): Future[SyncId] = addRequest(PostConvReceiptMode(id, receiptMode))
  def postConversationRole(id: ConvId, member: UserId, newRole: ConversationRole, origRole: ConversationRole): Future[SyncId] = addRequest(PostConvRole(id, member, newRole, origRole))
  def postLiking(id: ConvId, liking: Liking): Future[SyncId] = addRequest(PostLiking(id, liking))
  def postLastRead(id: ConvId, time: RemoteInstant) = addRequest(PostLastRead(id, time), priority = Priority.Low, delay = timeouts.messages.lastReadPostDelay)
  def postCleared(id: ConvId, time: RemoteInstant) = addRequest(PostCleared(id, time))
  def postOpenGraphData(conv: ConvId, msg: MessageId, time: RemoteInstant) = addRequest(PostOpenGraphMeta(conv, msg, time), priority = Priority.Low)
  def postReceipt(conv: ConvId, messages: Seq[MessageId], user: UserId, tpe: ReceiptType): Future[SyncId] = addRequest(PostReceipt(conv, messages, user, tpe), priority = Priority.Optional)
  def postAddBot(cId: ConvId, pId: ProviderId, iId: IntegrationId) = addRequest(PostAddBot(cId, pId, iId))
  def postRemoveBot(cId: ConvId, botId: UserId) = addRequest(PostRemoveBot(cId, botId))
  def postButtonAction(messageId: MessageId, buttonId: ButtonId, senderId: UserId): Future[SyncId] = addRequest(PostButtonAction(messageId, buttonId, senderId), forceRetry = true)
  def postProperty(key: PropertyKey, value: Boolean): Future[SyncId] = addRequest(PostBoolProperty(key, value), forceRetry = true)
  def postProperty(key: PropertyKey, value: Int): Future[SyncId] = addRequest(PostIntProperty(key, value), forceRetry = true)
  def postProperty(key: PropertyKey, value: String): Future[SyncId] = addRequest(PostStringProperty(key, value), forceRetry = true)
  def postFolders(): Future[SyncId] = addRequest(PostFolders, forceRetry = true)
  def postTrackingId(trackingId: TrackingId): Future[SyncId] = addRequest(PostTrackingId(trackingId))

  def registerPush(token: PushToken)    = addRequest(RegisterPushToken(token), priority = Priority.High, forceRetry = true)
  def deletePushToken(token: PushToken) = addRequest(DeletePushToken(token), priority = Priority.Low)

  def syncSelfClients() = addRequest(SyncSelfClients, priority = Priority.Critical)
  def syncSelfPermissions() = addRequest(SyncSelfPermissions, priority = Priority.High)
  def postClientLabel(id: ClientId, label: String) = addRequest(PostClientLabel(id, label))
  def postClientCapabilities(): Future[SyncId] = addRequest(PostClientCapabilities)
  def syncClients(users: Set[QualifiedId]): Future[SyncId] = addRequest(SyncClientsBatch(users))
  def syncPreKeys(qId: QualifiedId, clientIds: Set[ClientId]): Future[SyncId] = addRequest(SyncPreKeys(qId, clientIds))
  def syncProperties(): Future[SyncId] = addRequest(SyncProperties, forceRetry = true)

  def postSessionReset(conv: ConvId, user: UserId, client: ClientId): Future[SyncId] =
    addRequest(PostSessionReset(conv, user, client))
  def postSessionReset(conv: ConvId, qId: QualifiedId, client: ClientId): Future[SyncId] =
    addRequest(PostQualifiedSessionReset(conv, qId, client))

  override def performFullSync(): Future[Unit] = {
    verbose(l"performFullSync")
    for {
      id1        <- syncSelfUser()
      id2        <- syncSelfClients()
      id3        <- syncSelfPermissions()
      id4        <- syncTeam()
      id5        <- syncConversations()
      id6        <- syncConnections()
      id7        <- syncProperties()
      (id8, id9) <- syncUsers()
      id10       <- syncFolders()
      id11       <- syncLegalHoldRequest()
      _          =  verbose(l"waiting for full sync to finish...")
      _          <- service.await(Set(id1, id2, id3, id4, id5, id6, id7, id8, id9, id10, id11))
      _          =  verbose(l"... and done")
    } yield ()
  }

  private def syncUsers(): Future[(SyncId, SyncId)] =
    for {
      userMap      <- usersStorage.contents.head
      qualified    =  userMap.collect { case (id, u) if u.qualifiedId.nonEmpty => id -> u.qualifiedId }
      id1          <- syncQualifiedUsers(qualified.flatMap(_._2).toSet)
      nonQualified =  userMap.keySet -- qualified.keySet
      id2          <- syncUsers(nonQualified)
    } yield (id1, id2)

  override def deleteGroupConversation(teamId: TeamId, rConvId: RConvId) = {
    addRequest(DeleteGroupConversation(teamId, rConvId)).recoverWith {
      case e: Exception =>
        error(l"Error deleting group conv", e)
        Future.failed(e)
    }
  }
}

trait SyncHandler {
  import SyncHandler._
  def apply(account: UserId, req: SyncRequest, jobId: SyncId)(implicit reqInfo: RequestInfo): Future[SyncResult]
}

object SyncHandler {
  case class RequestInfo(attempt: Int, requestStart: Instant, network: Option[NetworkMode] = None)
}

class AccountSyncHandler(accounts: AccountsService) extends SyncHandler with DerivedLogTag {
  import SyncHandler._
  import Threading.Implicits.Background
  import com.waz.model.sync.SyncRequest._

  override def apply(accountId: UserId, req: SyncRequest, jobId: SyncId)(implicit reqInfo: RequestInfo): Future[SyncResult] = {
    verbose(l"SSM11<JOB:${jobId}> AccountSyncHandler apply for req ${req.cmd.name}: step 1")
    accounts.getZms(accountId).flatMap {
      case Some(zms) =>
        verbose(l"SSM11<JOB:${jobId}> AccountSyncHandler step 2")
        req match {
          case SyncSelfClients                                 => zms.otrClientsSync.syncSelfClients()
          case SyncClientsBatch(users)                         => zms.otrClientsSync.syncClients(users)
          case SyncPreKeys(qId, clientIds)                     => zms.otrClientsSync.syncPreKeys(QOtrClientIdMap.from(qId -> clientIds))
          case PostClientLabel(id, label)                      => zms.otrClientsSync.postLabel(id, label)
          case SyncConversation(convs)                         => zms.conversationSync.syncConversations(convs)
          case SyncConversations                               => zms.conversationSync.syncConversations()
          case SyncConvLink(conv)                              => zms.conversationSync.syncConvLink(conv)
          case SyncUser(u)                                     => zms.usersSync.syncUsers(u.toSeq: _*)
          case SyncQualifiedUsers(qIds)                        => zms.usersSync.syncQualifiedUsers(qIds)
          case SyncSearchResults(u)                            => zms.usersSync.syncSearchResults(u.toSeq: _*)
          case SyncQualifiedSearchResults(qIds)                => zms.usersSync.syncQualifiedSearchResults(qIds)
          case SyncSearchQuery(query)                          => zms.usersearchSync.syncSearchQuery(query)
          case SyncRichMedia(messageId)                        => zms.richmediaSync.syncRichMedia(messageId)
          case DeletePushToken(token)                          => zms.gcmSync.deleteGcmToken(token)
          case PostConnection(userId, name, message)           => zms.connectionsSync.postConnection(userId, name, message)
          case PostQualifiedConnection(qId)                    => zms.connectionsSync.postQualifiedConnection(qId)
          case PostConnectionStatus(userId, status)            => zms.connectionsSync.postConnectionStatus(userId, status)
          case PostQualifiedConnectionStatus(qId, status)      => zms.connectionsSync.postQualifiedConnectionStatus(qId, status)
          case SyncTeam                                        => zms.teamsSync.syncTeam()
          case SyncTeamData                                    => zms.teamsSync.syncTeamData()
          case SyncTeamMember(userId)                          => zms.teamsSync.syncMember(userId)
          case SyncConnections                                 => zms.connectionsSync.syncConnections()
          case SyncSelf                                        => zms.usersSync.syncSelfUser()
          case SyncSelfPermissions                             => zms.teamsSync.syncSelfPermissions()
          case DeleteGroupConversation(teamId, convId)         => zms.teamsSync.deleteConversations(teamId, convId)
          case DeleteAccount                                   => zms.usersSync.deleteAccount()
          case PostSelf(info)                                  => zms.usersSync.postSelfUser(info)
          case PostSelfPicture(assetId)                        => zms.usersSync.postSelfPicture(assetId)
          case PostSelfName(name)                              => zms.usersSync.postSelfName(name)
          case PostSelfAccentColor(color)                      => zms.usersSync.postSelfAccentColor(color)
          case PostAvailability(availability)                  => zms.usersSync.postAvailability(availability)
          case RegisterPushToken(token)                        => zms.gcmSync.registerPushToken(token)
          case PostLiking(convId, liking)                      => zms.reactionsSync.postReaction(convId, liking)
          case PostAddBot(cId, pId, iId)                       => zms.integrationsSync.addBot(cId, pId, iId)
          case PostRemoveBot(cId, botId)                       => zms.integrationsSync.removeBot(cId, botId)
          case PostButtonAction(messageId, buttonId, senderId) => zms.messagesSync.postButtonAction(messageId, buttonId, senderId)
          case PostDeleted(convId, msgId)                      => zms.messagesSync.postDeleted(convId, msgId)
          case PostLastRead(convId, time)                      => zms.lastReadSync.postLastRead(convId, time, jobId)
          case PostOpenGraphMeta(conv, msg, time)              => zms.openGraphSync.postMessageMeta(conv, msg, time)
          case PostRecalled(convId, msg, recall)               => zms.messagesSync.postRecalled(convId, msg, recall)
          case PostSessionReset(conv, user, client)            => zms.otrSync.postSessionReset(conv, user, client)
          case PostQualifiedSessionReset(conv, qId, client)    => zms.otrSync.postSessionReset(conv, qId, client)
          case PostReceipt(conv, msg, user, tpe)               => zms.messagesSync.postReceipt(conv, msg, user, tpe, jobId)
          case PostMessage(convId, messageId, time)            => zms.messagesSync.postMessage(convId, messageId, time)
          case PostAssetStatus(cid, mid, exp, status)          => zms.messagesSync.postAssetStatus(cid, mid, exp, status)
          case PostConvJoin(convId, u, role)                   => zms.conversationSync.postConversationMemberJoin(convId, u, role)
          case PostQualifiedConvJoin(convId, u, role)          => zms.conversationSync.postQualifiedConversationMemberJoin(convId, u, role)
          case PostConvLeave(convId, u)                        => zms.conversationSync.postConversationMemberLeave(convId, u)
          case PostQualifiedConvLeave(convId, qId)             => zms.conversationSync.postConversationMemberLeave(convId, qId)
          case PostConv(convId, u, name, team, access, accessRole, receiptMode, defRole) =>
            zms.conversationSync.postConversation(convId, u, name, team, access, accessRole, receiptMode, defRole)
          case PostQualified1To1Conv(convId, qId, team, access, accessRole, receiptMode, defRole) =>
            zms.conversationSync.postConversation(convId, qId, team, access, accessRole, receiptMode, defRole)
          case PostConvName(convId, name)                      => zms.conversationSync.postConversationName(convId, name)
          case PostConvReceiptMode(convId, receiptMode)        => zms.conversationSync.postConversationReceiptMode(convId, receiptMode)
          case PostConvState(convId, state)                    => zms.conversationSync.postConversationState(convId, state)
          case PostConvRole(convId, userId, newRole, origRole) => zms.conversationSync.postConversationRole(convId, userId, newRole, origRole)
          case PostTypingState(convId, ts)                     => zms.typingSync.postTypingState(convId, ts)
          case PostCleared(convId, time)                       => zms.clearedSync.postCleared(convId, time)
          case PostBoolProperty(key, value)                    => zms.propertiesSyncHandler.postProperty(key, value)
          case PostIntProperty(key, value)                     => zms.propertiesSyncHandler.postProperty(key, value)
          case PostStringProperty(key, value)                  => zms.propertiesSyncHandler.postProperty(key, value)
          case SyncProperties                                  => zms.propertiesSyncHandler.syncProperties()
          case PostFolders                                     => zms.foldersSyncHandler.postFolders()
          case SyncFolders                                     => zms.foldersSyncHandler.syncFolders()
          case PostTrackingId(trackingId)                      => zms.trackingSync.postNewTrackingId(trackingId)
          case SyncLegalHoldRequest                            => zms.legalHoldSync.syncLegalHoldRequest()
          case SyncClientsForLegalHold(convId)                 => zms.legalHoldSync.syncClientsForLegalHoldVerification(convId)
          case PostClientCapabilities                          => zms.otrClientsSync.postCapabilities()
          case Unknown                                         =>
            verbose(l"SSM11<JOB:${jobId}> AccountSyncHandler step 4:Unknown")
            Future.successful(Failure("Unknown sync request"))
      }
      case None => {
        verbose(l"SSM11<JOB:${jobId}> AccountSyncHandler step 4:None")
        Future.successful(Failure(s"Account $accountId is not logged in"))
      }
  }
  }
}
