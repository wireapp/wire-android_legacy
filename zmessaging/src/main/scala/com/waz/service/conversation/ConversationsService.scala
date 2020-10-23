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

import com.waz.api.ErrorType
import com.waz.api.IConversation.Access
import com.waz.api.impl.ErrorResponse
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.ConversationType.isOneToOne
import com.waz.model.ConversationData.{ConversationType, Link, getAccessAndRoleForGroupConv}
import com.waz.model._
import com.waz.service._
import com.waz.service.assets.AssetService
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.{NotificationService, PushService}
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.{ConversationsClient, ErrorOr}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals.{AggregatingSignal, EventContext, Signal}

import scala.collection.{breakOut, mutable}
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.control.{NoStackTrace, NonFatal}

trait ConversationsService {
  def convStateEventProcessingStage: EventScheduler.Stage
  def processConversationEvent(ev: ConversationStateEvent, selfUserId: UserId, retryCount: Int = 0): Future[Any]
  def activeMembersData(conv: ConvId): Signal[Seq[ConversationMemberData]]
  def convMembers(convId: ConvId): Signal[Map[UserId, ConversationRole]]
  def updateConversationsWithDeviceStartMessage(conversations: Seq[ConversationResponse], roles: Map[RConvId, Set[ConversationRole]]): Future[Unit]
  def updateRemoteId(id: ConvId, remoteId: RConvId): Future[Unit]
  def setConversationArchived(id: ConvId, archived: Boolean): Future[Option[ConversationData]]
  def setReceiptMode(id: ConvId, receiptMode: Int): Future[Option[ConversationData]]
  def onMemberAddFailed(conv: ConvId, users: Set[UserId], error: Option[ErrorType], resp: ErrorResponse): Future[Unit]
  def onUpdateRoleFailed(conv: ConvId, user: UserId, newRole: ConversationRole, origRole: ConversationRole, resp: ErrorResponse): Future[Unit]
  def groupConversation(convId: ConvId): Signal[Boolean]
  def isGroupConversation(convId: ConvId): Future[Boolean]
  def isWithService(convId: ConvId): Future[Boolean]

  def setToTeamOnly(convId: ConvId, teamOnly: Boolean): ErrorOr[Unit]
  def createLink(convId: ConvId): ErrorOr[Link]
  def removeLink(convId: ConvId): ErrorOr[Unit]

  /**
    * This method is used to update conversation state whenever we detect a user on sending or receiving a message
    * who we didn't expect to be there - we need to expose these users to the self user
    */
  def addUnexpectedMembersToConv(convId: ConvId, us: Set[UserId]): Future[Unit]

  def setConversationRole(id: ConvId, userId: UserId, role: ConversationRole): Future[Unit]

  def deleteConversation(rConvId: RConvId): Future[Unit]
  def conversationName(convId: ConvId): Signal[Name]
  def deleteMembersFromConversations(members: Set[UserId]): Future[Unit]
}

class ConversationsServiceImpl(teamId:          Option[TeamId],
                               selfUserId:      UserId,
                               push:            PushService,
                               users:           UserService,
                               usersStorage:    UsersStorage,
                               membersStorage:  MembersStorage,
                               convsStorage:    ConversationStorage,
                               content:         ConversationsContentUpdater,
                               sync:            SyncServiceHandle,
                               errors:          ErrorsService,
                               messages:        MessagesService,
                               msgContent:      MessagesContentUpdater,
                               userPrefs:       UserPreferences,
                               eventScheduler:  => EventScheduler,
                               client:          ConversationsClient,
                               selectedConv:    SelectedConversationService,
                               syncReqService:  SyncRequestService,
                               assetService:    AssetService,
                               receiptsStorage: ReadReceiptsStorage,
                               notificationService: NotificationService,
                               foldersService:  FoldersService,
                               rolesService:    ConversationRolesService
                              ) extends ConversationsService with DerivedLogTag {

  import Threading.Implicits.Background

  //On conversation changed, update the state of the access roles as part of migration, then check for a link if necessary
  selectedConv.selectedConversationId {
    case Some(convId) => convsStorage.get(convId).flatMap {
      case Some(conv) if conv.accessRole.isEmpty =>
        for {
          syncId        <- sync.syncConversations(Set(conv.id))
          _             <- syncReqService.await(syncId)
          Some(updated) <- content.convById(conv.id)
        } yield if (updated.access.contains(Access.CODE)) sync.syncConvLink(conv.id)

      case _ => Future.successful({})
    }
    case None => //
  }

  if (teamId.isDefined)
    for {
      removeTeamMembers <- userPrefs.preference(UserPreferences.RemoveUncontactedTeamMembers).apply()
    } if (removeTeamMembers)
      for {
        members  <- membersStorage.contents.map(_.keys.map(_._1)).head
        users    <- usersStorage.contents.map(_.withFilter(!_._2.deleted).map(_._1)).head
        toRemove =  users.toSet -- members.toSet
        _        <- if (toRemove.nonEmpty) usersStorage.updateAll2(toRemove, _.copy(deleted = true))
                    else Future.successful(())
        _        <- userPrefs.setValue(UserPreferences.RemoveUncontactedTeamMembers, false)
        _        =  verbose(l"Uncontacted team members removed for the team $teamId")
      } yield ()

  val convStateEventProcessingStage = EventScheduler.Stage[ConversationStateEvent] { (_, events) =>
    RichFuture.traverseSequential(events)(processConversationEvent(_, selfUserId))
  }

  push.onHistoryLost { req =>
    verbose(l"onSlowSyncNeeded($req)")
    // TODO: this is just very basic implementation creating empty message
    // This should be updated to include information about possibly missed changes
    // this message will be shown rarely (when notifications stream skips data)
    convsStorage.list.flatMap(messages.addHistoryLostMessages(_, selfUserId))
  }

  errors.onErrorDismissed {
    case ErrorData(_, ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER, _, _, Some(convId), _, _, _, _) =>
      deleteTempConversation(convId)
    case ErrorData(_, ErrorType.CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION, userIds, _, Some(convId), _, _, _, _) => Future.successful(())
    case ErrorData(_, ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION, userIds, _, Some(convId), _, _, _, _) => Future.successful(())
    case ErrorData(_, ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION, _, _, Some(conv), _, _, _, _) =>
      convsStorage.setUnknownVerification(conv)
  }

  def processConversationEvent(ev: ConversationStateEvent, selfUserId: UserId, retryCount: Int = 0) = ev match {
    case CreateConversationEvent(_, time, from, data) =>
      updateConversation(data).flatMap { case (_, created) => Future.traverse(created) { created =>
        messages.addConversationStartMessage(
          created.id,
          from,
          (data.members.keySet + selfUserId).filter(_ != from),
          created.name,
          readReceiptsAllowed = created.readReceiptsAllowed,
          time = Some(time)
        )
      }}

    case ConversationEvent(rConvId, _, _) =>
      content.convByRemoteId(rConvId).flatMap {
        case Some(conv) => processUpdateEvent(conv, ev)
        case None if retryCount > 3 => successful(())
        case None =>
          ev match {
            case MemberJoinEvent(_, time, from, ids, us, _) if from != selfUserId =>
              // usually ids should be exactly the same set as members, but if not, we add surplus ids as members with the Member role
              val membersWithRoles = us ++ ids.map(_ -> ConversationRole.MemberRole).toMap
              // this happens when we are added to group conversation
              for {
                conv       <- convsStorage.insert(ConversationData(ConvId(), rConvId, None, from, ConversationType.Group, lastEventTime = time))
                _          <- membersStorage.updateOrCreateAll(conv.id, Map(from -> ConversationRole.AdminRole) ++ membersWithRoles)
                sId        <- sync.syncConversations(Set(conv.id))
                _          <- syncReqService.await(sId)
                Some(conv) <- convsStorage.get(conv.id)
                _          <- if (conv.receiptMode.exists(_ > 0)) messages.addReceiptModeIsOnMessage(conv.id) else Future.successful(None)
                _          <- messages.addMemberJoinMessage(conv.id, from, membersWithRoles.keySet)
              } yield {}
            case _ =>
              warn(l"No conversation data found for event: $ev on try: $retryCount")
              content.processConvWithRemoteId(rConvId, retryAsync = true) { processUpdateEvent(_, ev) }
          }
      }
  }

  private def processUpdateEvent(conv: ConversationData, ev: ConversationEvent) = ev match {
    case DeleteConversationEvent(_, time, from) => (for {
      _ <- notificationService.displayNotificationForDeletingConversation(from, time, conv)
      _ <- deleteConversation(conv)
    } yield ()).recoverWith {
      case e: Exception => error(l"error while processing DeleteConversationEvent", e)
      Future.successful(())
    }

    case RenameConversationEvent(_, _, _, name) => content.updateConversationName(conv.id, name)

    case MemberJoinEvent(_, _, _, ids, us, _) =>
      // usually ids should be exactly the same set as members, but if not, we add surplus ids as members with the Member role
      val membersWithRoles = us ++ ids.map(_ -> ConversationRole.MemberRole).toMap
      val selfAdded = membersWithRoles.keySet.contains(selfUserId)//we were re-added to a group and in the meantime might have missed events
      for {
        convSync   <- if (selfAdded) sync.syncConversations(Set(conv.id)).map(Option(_)) else Future.successful(None)
        syncId     <- users.syncIfNeeded(membersWithRoles.keySet)
        _          <- syncId.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
        _          <- membersStorage.updateOrCreateAll(conv.id, membersWithRoles)
        _          <- if (selfAdded) content.setConvActive(conv.id, active = true) else successful(None)
        _          <- convSync.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
        Some(conv) <- convsStorage.get(conv.id)
        _          <- if (selfAdded && conv.receiptMode.exists(_ > 0)) messages.addReceiptModeIsOnMessage(conv.id) else Future.successful(None)
      } yield ()

    case MemberLeaveEvent(_, time, from, userIds) =>
      val userIdSet = userIds.toSet
      for {
        syncId         <- users.syncIfNeeded(userIdSet -- Set(selfUserId))
        _              <- syncId.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
        _              <- deleteMembers(conv.id, userIdSet, Some(from), sendSystemMessage = true)
        selfUserLeaves =  userIdSet.contains(selfUserId)
        _              <- if (selfUserLeaves) content.setConvActive(conv.id, active = false) else Future.successful(())
                          // if the user removed themselves from another device, archived on this device
        _              <- if (selfUserLeaves && from == selfUserId)
                            content.updateConversationState(conv.id, ConversationState(Option(true), Option(time)))
                          else
                            Future.successful(None)
      } yield ()

    case MemberUpdateEvent(_, _, userId, state) =>
      for {
        _      <- content.updateConversationState(conv.id, state)
        _      <- (state.target, state.conversationRole) match {
                    case (Some(id), Some(role)) => membersStorage.updateOrCreate(conv.id, id, role)
                    case _                      => Future.successful(())
                  }
        syncId <- users.syncIfNeeded(Set(userId))
        _      <- syncId.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
      } yield ()

    case ConnectRequestEvent(_, _, from, _, recipient, _, _) =>
      membersStorage.updateOrCreateAll(conv.id, Map(from -> ConversationRole.AdminRole, recipient -> ConversationRole.AdminRole)).flatMap { added =>
        users.syncIfNeeded(added.map(_.userId))
      }

    case ConversationAccessEvent(_, _, _, access, accessRole) =>
      content.updateAccessMode(conv.id, access, Some(accessRole))

    case ConversationCodeUpdateEvent(_, _, _, l) =>
      convsStorage.update(conv.id, _.copy(link = Some(l)))

    case ConversationCodeDeleteEvent(_, _, _) =>
      convsStorage.update(conv.id, _.copy(link = None))

    case ConversationReceiptModeEvent(_, _, _, receiptMode) =>
      content.updateReceiptMode(conv.id, receiptMode = receiptMode)

    case MessageTimerEvent(_, _, _, duration) =>
      convsStorage.update(conv.id, _.copy(globalEphemeral = duration))

    case _ => successful(())
  }

  override def activeMembersData(conv: ConvId): Signal[Seq[ConversationMemberData]] = {
    val onConvMemberDataChanged =
      membersStorage
        .onChanged.map(_.filter(_.convId == conv).map(m => m.userId -> (Option(m), true)))
        .zip(membersStorage.onDeleted.map(_.filter(_._2 == conv).map(_._1 -> (None, false)))).map(_.toMap)

    new AggregatingSignal[Map[UserId, (Option[ConversationMemberData], Boolean)], Seq[ConversationMemberData]](
      onConvMemberDataChanged,
      membersStorage.getByConv(conv),
      { (current, changes) =>
        val (active, inactive) = changes.partition(_._2._2)
        val inactiveIds = inactive.keySet
        current.filter(m => !inactiveIds.contains(m.userId)) ++ active.values.collect { case (Some(m), _) => m }
      }
    )
  }

  override def convMembers(convId: ConvId): Signal[Map[UserId, ConversationRole]] =
    activeMembersData(convId).map(_.map(m => m.userId -> ConversationRole.getRole(m.role)).toMap)

  override def updateConversationsWithDeviceStartMessage(conversations: Seq[ConversationResponse], roles: Map[RConvId, Set[ConversationRole]]): Future[Unit] =
    for {
      (_, created) <- updateConversations(conversations, roles)
      _            <- messages.addDeviceStartMessages(created, selfUserId)
    } yield {}

  // ask the backend for the roles and only then update the conversations
  private def updateConversation(response: ConversationResponse): Future[(Seq[ConversationData], Seq[ConversationData])] =
    client.loadConversationRoles(Set(response.id)).flatMap(roles => updateConversations(Seq(response), roles))

  private def findExistingId(responses: Seq[ConversationResponse]): Future[Seq[(ConvId, ConversationResponse)]] = convsStorage { convsById =>
    def byRemoteId(id: RConvId) = convsById.values.find(_.remoteId == id)

    responses.map { resp =>
      val newId =
        if (isOneToOne(resp.convType))
          resp.members.keys.find(_ != selfUserId).fold(ConvId())(m => ConvId(m.str))
        else
          ConvId(resp.id.str)

      val matching = byRemoteId(resp.id).orElse {
        convsById.get(newId).orElse {
          if (isOneToOne(resp.convType)) None
          else byRemoteId(ConversationsService.generateTempConversationId(resp.members.keySet + selfUserId))
        }
      }

      returning((matching.fold(newId)(_.id), resp)) { r =>
        verbose(l"Returning conv id pair $r, isOneToOne: ${isOneToOne(resp.convType)}")
      }
    }
  }

  private def updateOrCreate(newLocalId: ConvId, resp: ConversationResponse, created: mutable.ArrayBuffer[ConversationData]) = { prev: Option[ConversationData] =>
    returning(prev.getOrElse(ConversationData(id = newLocalId, hidden = isOneToOne(resp.convType) && resp.members.size <= 1))
      .copy(
        remoteId        = resp.id,
        name            = resp.name.filterNot(_.isEmpty),
        creator         = resp.creator,
        convType        = prev.map(_.convType).filter(oldType => isOneToOne(oldType) && resp.convType != ConversationType.OneToOne).getOrElse(resp.convType),
        team            = resp.team,
        muted           = if (resp.muted == MuteSet.OnlyMentionsAllowed && teamId.isEmpty) MuteSet.AllMuted else resp.muted,
        muteTime        = resp.mutedTime,
        archived        = resp.archived,
        archiveTime     = resp.archivedTime,
        access          = resp.access,
        accessRole      = resp.accessRole,
        link            = resp.link,
        globalEphemeral = resp.messageTimer,
        receiptMode     = resp.receiptMode

      ))(c => if (prev.isEmpty) created += c)
  }

  private def updateConversationData(responses: Seq[ConversationResponse]): Future[(Set[ConversationData], Seq[ConversationData])] = {
    val created = mutable.ArrayBuffer[ConversationData]()

    for {
      withId <- findExistingId(responses)
      convs  <- convsStorage.updateOrCreateAll(withId.map {
                  case (localId, resp) => localId -> updateOrCreate(localId, resp, created)
                } (breakOut))
    } yield (convs, created)
  }

  private def updateMembers(responses: Seq[ConversationResponse]): Future[Unit] =
    for {
      convs         <- content.convsByRemoteId(responses.map(_.id).toSet)
      toUpdate      =  responses.map(c => (c.id, c.members)).flatMap {
                         case (remoteId, members) => convs.get(remoteId).map(c => c.id -> (members + (c.creator -> ConversationRole.AdminRole)))
                       }.toMap
      activeUsers   <- membersStorage.getActiveUsers2(convs.map(_._2.id).toSet)
      _             <- membersStorage.setAll(toUpdate)
      usersLeft     <- membersStorage.getByUsers(activeUsers.flatMap(_._2).toSet).map(_.map(_.userId).toSet)
      usersRemoved  =  activeUsers.map { case (cId, uIds) => cId -> (uIds -- usersLeft) }.filter(_._2.nonEmpty)
      _             <- Future.traverse(usersRemoved) {
                         case (cId, uIds) => messages.addMemberLeaveMessage(cId, UserId(), uIds) // UserId() == unknown remover
                       }
      _             <- Future.traverse(usersRemoved) {
                         case (cId, _) => renameConversationIfNeeded(cId, activeUsers(cId))
                       }
      usersToDelete =  usersRemoved.flatMap(_._2).toSet
      _             <- if (usersToDelete.nonEmpty) usersStorage.updateAll2(usersToDelete, _.copy(deleted = true))
                       else Future.successful(())
    } yield ()

  override def deleteMembersFromConversations(members: Set[UserId]): Future[Unit] =
    for {
      convMembers <- membersStorage.getByUsers(members)
      _           <- Future.sequence(convMembers.groupBy(_.convId).map { case (convId, uIds) =>
                       deleteMembers(convId, uIds.map(_.userId).toSet, remover = None, sendSystemMessage = true)
                     })
    } yield ()

  private def deleteMembers(convId: ConvId): Future[Unit] =
    for {
      userIds <- membersStorage.getActiveUsers(convId).map(_.toSet)
      _       <- deleteMembers(convId, userIds, remover = None, sendSystemMessage = false)
    } yield ()

  private def deleteMembers(convId: ConvId, toRemove: Set[UserId], remover: Option[UserId], sendSystemMessage: Boolean): Future[Unit] =
    for {
      _              <- membersStorage.remove(convId, toRemove)
      _              <- renameConversationIfNeeded(convId, toRemove)
      isGroup        <- if (sendSystemMessage) isGroupConversation(convId) else Future.successful(false)
      _              <- if (isGroup)
                          messages.addMemberLeaveMessage(convId, remover.getOrElse(UserId()), toRemove) // UserId() == unknown remover
                        else
                          Future.successful(())
      stillInTeam    <- membersStorage.getByUsers(toRemove).map(_.map(_.userId).toSet)
      usersToDelete  =  toRemove -- stillInTeam
      _              <- if (usersToDelete.nonEmpty) usersStorage.updateAll2(usersToDelete, _.copy(deleted = true))
                        else Future.successful(())
    } yield ()

  // The conversation needs to be renamed if:
  // 1. The last users other than self are being removed from it (i.e. afterwards only self or nobody stays in the conv)
  // 2. AND the conv doesn't have its name set already (e.g. real group convs usually have)
  // 3. AND we have names of the last leaving users (in large teams it's not sure)
  private def renameConversationIfNeeded(convId: ConvId, usersRemoved: Set[UserId]): Future[Unit] = {
    def updateConversationNameWithUserNames(names: Seq[Name]): Future[Unit] =
      createConversationName(names) match {
        case newConvName if newConvName.nonEmpty =>
          content.updateConversationName(convId, newConvName).map(_ => ())
        case _ => Future.successful(())
      }

    def getUserNames(userIds: Set[UserId]): Future[Seq[Name]] =
      users.userNames.map(names => userIds.toSeq.flatMap(names.get).sortBy(_.str)).head

    def isConversationNameEmpty: Future[Boolean] =
      convsStorage.get(convId).map {
        case Some(conv) => conv.name.forall(_.isEmpty)
        case None       => false
      }

    def isConversationStillActive: Future[Boolean] =
      membersStorage.getActiveUsers(convId).map(uIds => (uIds.toSet - selfUserId).nonEmpty)

    val otherRemovedUserIds = usersRemoved - selfUserId
    if (otherRemovedUserIds.nonEmpty)
      isConversationStillActive.flatMap {
        case true  => Future.successful(())
        case false => isConversationNameEmpty.flatMap {
          case true  => getUserNames(otherRemovedUserIds).flatMap(updateConversationNameWithUserNames)
          case false => Future.successful(())
        }
      }
    else Future.successful(())
  }

  private def updateRoles(convIds: Map[ConvId, RConvId], roles: Map[RConvId, Set[ConversationRole]]): Future[Unit] =
    Future.sequence(convIds.collect {
      case (cId, rId) if roles.contains(rId) => rolesService.createOrUpdate(cId, roles(rId))
    }).map(_ => ())

  private def updateConversations(responses: Seq[ConversationResponse],
                                  roles:     Map[RConvId, Set[ConversationRole]]
                                 ): Future[(Seq[ConversationData], Seq[ConversationData])] =
    for {
      (convs, created) <- updateConversationData(responses)
      _                <- updateRoles(convs.map(data => data.id -> data.remoteId).toMap, roles)
      _                <- updateMembers(responses)
      _                <- users.syncIfNeeded(responses.flatMap(_.members.keys).toSet)
    } yield (convs.toSeq, created)

  def updateRemoteId(id: ConvId, remoteId: RConvId): Future[Unit] =
    convsStorage.update(id, c => c.copy(remoteId = remoteId)).map(_ => ())

  def setConversationArchived(id: ConvId, archived: Boolean) = content.updateConversationArchived(id, archived) flatMap {
    case Some((_, conv)) =>
      sync.postConversationState(id, ConversationState(archived = Some(conv.archived), archiveTime = Some(conv.archiveTime))) map { _ => Some(conv) }
    case None =>
      Future successful None
  }

  def setReceiptMode(id: ConvId, receiptMode: Int) = content.updateReceiptMode(id, receiptMode).flatMap {
    case Some((_, conv)) =>
      sync.postReceiptMode(id, receiptMode).map(_ => Some(conv))
    case None =>
      Future successful None
  }

  private def deleteTempConversation(convId: ConvId) = for {
    _ <- convsStorage.remove(convId)
    _ <- deleteMembers(convId)
    _ <- msgContent.deleteMessagesForConversation(convId)
    _ <- rolesService.removeByConvId(convId)
  } yield ()

  override def deleteConversation(rConvId: RConvId): Future[Unit] = {
    content.convByRemoteId(rConvId) flatMap {
      case Some(conv) => deleteConversation(conv)
      case None =>
        verbose(l"Conversation w/ remote id $rConvId not found. Ignoring deletion.")
        Future.successful(())
    }
  }

  // NOTE: This could be simpler if we didn't care about backward compatibility
  override def conversationName(convId: ConvId): Signal[Name] =
    convsStorage.optSignal(convId).flatMap {
      case None =>
        Signal.const(Name.Empty)
      case Some(conv) if conv.name.exists(_.nonEmpty) && !ConversationType.isOneToOne(conv.convType) =>
        // some old 1:1 convs have names defined but they should use the other user's name
        Signal.const(conv.name.get)
      case Some(conv) if ConversationType.isOneToOne(conv.convType) =>
        users.userNames.map(_.getOrElse(UserId(conv.id.str), Name.Empty))
      case Some(conv) =>
        for {
          members   <- activeMembersData(conv.id)
          memberIds =  members.filterNot(_.userId == selfUserId).take(4).map(_.userId).toSet
          userNames <- users.userNames.map(names => memberIds.flatMap(names.get))
        } yield
          createConversationName(userNames.toSeq)
    }

  private def createConversationName(userNames: Seq[Name]): Name =
    if (userNames.isEmpty) Name.Empty
    else if (userNames.size == 1) userNames.head
    // This is for backward compatibility: all new real group conversations should have their names set.
    // For those who don't, we create the name from first four members' names.
    else Name(userNames.map(_.str).sorted.mkString(", "))

  private def deleteConversation(convData: ConversationData): Future[Unit] = (for {
      convMessageIds <- messages.findMessageIds(convData.id)
      convId         =  convData.id
      assetIds       <- messages.getAssetIds(convMessageIds)
      _              <- assetService.deleteAll(assetIds)
      _              <- convsStorage.remove(convId)
      _              <- deleteMembers(convId)
      _              <- msgContent.deleteMessagesForConversation(convId)
      _              <- receiptsStorage.removeAllForMessages(convMessageIds)
      _              <- checkCurrentConversationDeleted(convId)
      _              <- foldersService.removeConversationFromAll(convId, uploadAllChanges = false)
      _              <- rolesService.removeByConvId(convData.id)
    } yield ()).recoverWith {
      case ex: Exception =>
        error(l"error while deleting conversation", ex)
        Future.successful(())
    }

  private def checkCurrentConversationDeleted(convId: ConvId): Future[Unit] =
    selectedConv.selectedConversationId.head.map { selectedConvId =>
      if (selectedConvId.contains(convId)) selectedConv.selectConversation(None)
      else Future.successful(())
    }

  def onMemberAddFailed(conv: ConvId, users: Set[UserId], err: Option[ErrorType], resp: ErrorResponse) =
    for {
      _ <- err.fold(Future.successful(()))(e => errors.addErrorWhenActive(ErrorData(e, resp, conv, users)).map(_ => ()))
      _ <- membersStorage.remove(conv, users)
      _ <- messages.removeLocalMemberJoinMessage(conv, users)
      _ =  error(l"onMembersAddFailed($conv, $users, $err, $resp)")
    } yield ()

  def onUpdateRoleFailed(conv: ConvId, user: UserId, newRole: ConversationRole, origRole: ConversationRole, resp: ErrorResponse): Future[Unit] =
    for {
      _ <- errors.addErrorWhenActive(ErrorData(ErrorType.CANNOT_CHANGE_CONVERSATION_ROLE, resp, conv, Set(user)))
      _ <- membersStorage.updateOrCreate(conv, user, origRole)
      _ =  error(l"Failed to change the conversation role from $origRole to $newRole in the conversation $conv for the user $user")
    } yield ()

  override def groupConversation(convId: ConvId): Signal[Boolean] =
    convsStorage.optSignal(convId).flatMap {
    case None       => Signal.const(true) // the conversation might have been deleted - only group conversations can be deleted
    case Some(conv) => groupConversation(conv)
  }

  private def groupConversation(conv: ConversationData) =
    (conv.convType, conv.name, conv.team) match {
      case (convType, _, _) if convType != ConversationType.Group => Signal.const(false)
      case (_, Some(_), _) | (_, _, None)                         => Signal.const(true)
      case _ =>
        membersStorage.activeMembers(conv.id).map(ms => !(ms.contains(selfUserId) && ms.size <= 2))
    }

  override def isGroupConversation(convId: ConvId): Future[Boolean] = groupConversation(convId).head

  def isWithService(convId: ConvId) =
    membersStorage.getActiveUsers(convId)
      .flatMap(usersStorage.getAll)
      .map(_.flatten.exists(_.isWireBot))

  def setToTeamOnly(convId: ConvId, teamOnly: Boolean) =
    teamId match {
      case None => Future.successful(Left(ErrorResponse.internalError("Private accounts can't be set to team-only or guest room access modes")))
      case Some(_) =>
        (for {
          true <- isGroupConversation(convId)
          (ac, ar) = getAccessAndRoleForGroupConv(teamOnly, teamId)
          Some((old, upd)) <- content.updateAccessMode(convId, ac, Some(ar))
          resp <-
            if (old.access != upd.access || old.accessRole != upd.accessRole) {
              client.postAccessUpdate(upd.remoteId, ac, ar)
            }.future.flatMap {
              case Right(_) => Future.successful(Right {})
              case Left(err) =>
                //set mode back on request failed
                content.updateAccessMode(convId, old.access, old.accessRole, old.link).map(_ => Left(err))
            }
            else Future.successful(Right {})
        } yield resp).recover {
          case NonFatal(e) =>
            warn(l"Unable to set team only mode on conversation", e)
            Left(ErrorResponse.internalError("Unable to set team only mode on conversation"))
        }
    }

  override def createLink(convId: ConvId) =
    (for {
      Some(conv) <- content.convById(convId) if conv.isGuestRoom || conv.isWirelessLegacy
      modeResp   <- if (conv.isWirelessLegacy) setToTeamOnly(convId, teamOnly = false) else Future.successful(Right({})) //upgrade legacy convs
      linkResp   <- modeResp match {
        case Right(_) => client.createLink(conv.remoteId).future
        case Left(err) => Future.successful(Left(err))
      }
      _ <- linkResp match {
        case Right(l) => convsStorage.update(convId, _.copy(link = Some(l)))
        case _ => Future.successful({})
      }
    } yield linkResp)
      .recover {
        case NonFatal(e) =>
          error(l"Failed to create link", e)
          Left(ErrorResponse.internalError("Unable to create link for conversation"))
      }

  override def removeLink(convId: ConvId) =
    (for {
      Some(conv) <- content.convById(convId)
      resp       <- client.removeLink(conv.remoteId).future
      _ <- resp match {
        case Right(_) => convsStorage.update(convId, _.copy(link = None))
        case _ => Future.successful({})
      }
    } yield resp)
      .recover {
        case NonFatal(e) =>
          error(l"Failed to remove link", e)
          Left(ErrorResponse.internalError("Unable to remove link for conversation"))
      }

  override def addUnexpectedMembersToConv(convId: ConvId, us: Set[UserId]): Future[Unit] = {
    membersStorage.getByConv(convId).map(_.map(_.userId).toSet).map(us -- _).flatMap {
      case unexpected if unexpected.nonEmpty =>
        for {
          _ <- users.syncIfNeeded(unexpected)
          _ <- membersStorage.updateOrCreateAll(convId, unexpected.map(_ -> ConversationRole.MemberRole).toMap)
          _ <- Future.traverse(unexpected)(u => messages.addMemberJoinMessage(convId, u, Set(u), forceCreate = true)) //add a member join message for each user discovered
        } yield {}
      case _ => Future.successful({})
    }
  }

  override def setConversationRole(convId: ConvId, userId: UserId, newRole: ConversationRole): Future[Unit] =
    for {
      member   <- membersStorage.get((userId, convId))
      origRole =  member.map(m => ConversationRole.getRole(m.role))
      _        <- membersStorage.updateOrCreate(convId, userId, newRole)
      _        <- sync.postConversationRole(convId, userId, newRole, origRole.getOrElse(ConversationRole.MemberRole))
    } yield ()
}

object ConversationsService {
  import scala.concurrent.duration._

  val RetryBackoff = new ExponentialBackoff(500.millis, 3.seconds)

  /**
   * Generate temp ConversationID to identify conversations which don't have a RConvId yet
   */
  def generateTempConversationId(users: Set[UserId]) =
    RConvId(users.toSeq.map(_.toString).sorted.foldLeft("")(_ + _))
}
