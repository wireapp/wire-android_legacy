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
package com.waz.sync.handler

import com.waz.api.ErrorType
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MembersStorage, MessagesStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Retry, Success}
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.sync.client.ConversationsClient.{ConversationInitState, ConversationResponse}
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.Right
import scala.util.control.NonFatal

object ConversationsSyncHandler {
  val PostMembersLimit = 256
}

class ConversationsSyncHandler(selfUserId:          UserId,
                               teamId:              Option[TeamId],
                               userService:         UserService,
                               messagesStorage:     MessagesStorage,
                               messagesService:     MessagesService,
                               convService:         ConversationsService,
                               convs:               ConversationsContentUpdater,
                               convEvents:          ConversationOrderEventsService,
                               convStorage:         ConversationStorage,
                               errorsService:       ErrorsService,
                               conversationsClient: ConversationsClient,
                               genericMessages:     GenericMessageService,
                               rolesService:        ConversationRolesService,
                               membersStorage:      MembersStorage
                              ) extends DerivedLogTag {

  import Threading.Implicits.Background
  import com.waz.sync.handler.ConversationsSyncHandler._

  // optimization: same team conversations and private conversations use default roles so we don't have to ask the backend
  private def loadConversationRoles(resps: Seq[ConversationResponse]) = {
    val (otherTeamResps, teamAndPrivResps) = resps.partition(r => r.team.isDefined && r.team != teamId)
    rolesService.defaultRoles.head.flatMap { defRoles =>
      conversationsClient
        .loadConversationRoles(otherTeamResps.map(_.id).toSet, defRoles)
        .map(_ ++ teamAndPrivResps.map(r => r.id -> defRoles).toMap)
    }
  }

  def syncConversations(ids: Set[ConvId]): Future[SyncResult] =
    Future.sequence(ids.map(convs.convById)).flatMap { convs =>
      val remoteIds = convs.collect { case Some(conv) => conv.remoteId }

      if (remoteIds.size != convs.size) error(l"syncConversations($ids) - some conversations were not found in local db, skipping")

      conversationsClient.loadConversations(remoteIds).future flatMap {
        case Right(resps) =>
          loadConversationRoles(resps).flatMap { roles =>
            debug(l"syncConversations received ${resps.size}, ${roles.size}")
            convService.updateConversationsWithDeviceStartMessage(resps, roles).map(_ => Success)
          }

        case Left(error) =>
          warn(l"ConversationsClient.syncConversations($ids) failed with error: $error")
          Future.successful(SyncResult(error))
      }
    }

  def syncConversations(start: Option[RConvId] = None, rIdsFromBackend: Set[RConvId] = Set.empty): Future[SyncResult] =
    conversationsClient.loadConversations(start).future.flatMap {
      case Right(ConversationsResult(responses, hasMore)) =>
        loadConversationRoles(responses).flatMap { roles =>
          convService.updateConversationsWithDeviceStartMessage(responses, roles).flatMap { _ =>
            if (hasMore)
              syncConversations(responses.lastOption.map(_.id), rIdsFromBackend ++ responses.map(_.id))
            else
              removeConvsMissingOnBackend(rIdsFromBackend ++ responses.map(_.id)).map(_ => Success)
          }
        }
      case Left(error) =>
        Future.successful(SyncResult(error))
    }

  private def removeConvsMissingOnBackend(rIdsFromBackend: Set[RConvId]) =
    for {
      rIdsFromStorage <- convService.remoteIds
      missing         =  rIdsFromStorage -- rIdsFromBackend
      _               <- Future.sequence(missing.map(convService.deleteConversation))
    } yield ()

  def postConversationName(id: ConvId, name: Name): Future[SyncResult] =
    postConv(id) { conv => conversationsClient.postName(conv.remoteId, name).future }

  def postConversationReceiptMode(id: ConvId, receiptMode: Int): Future[SyncResult] =
    withConversation(id) { conv =>
      conversationsClient.postReceiptMode(conv.remoteId, receiptMode).map(SyncResult(_))
    }

  def postConversationRole(id: ConvId, userId: UserId, newRole: ConversationRole, origRole: ConversationRole): Future[SyncResult] =
    withConversation(id) { conv =>
      conversationsClient.postConversationRole(conv.remoteId, userId, newRole).future.flatMap {
        case Right(_) =>
          Future.successful(Success)
        case Left(error) =>
          convService.onUpdateRoleFailed(id, userId, newRole, origRole, error).map(_ => SyncResult(error))
      }
    }

  def postConversationMemberJoin(id: ConvId, members: Set[UserId], defaultRole: ConversationRole): Future[SyncResult] = withConversation(id) { conv =>
    def post(users: Set[UserId]) = conversationsClient.postMemberJoin(conv.remoteId, users, defaultRole).future flatMap {
      case Left(resp @ ErrorResponse(status, _, label)) =>
        val errTpe = (status, label) match {
          case (403, "not-connected")             => Some(ErrorType.CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION)
          case (403, "too-many-members")          => Some(ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION)
          case (412, "missing-legalhold-consent") => Some(ErrorType.CANNOT_ADD_PARTICIPANT_WITH_MISSING_LEGAL_HOLD_CONSENT)
          case _                                  => None
        }
        convService
          .onMemberAddFailed(id, users, errTpe, resp)
          .map(_ => SyncResult(resp))
      case resp =>
        verbose(l"postConversationMemberJoin($id, $members, $defaultRole): $resp")
        postConvRespHandler(resp)
    }

    Future.traverse(members.grouped(PostMembersLimit))(post) map { _.find(_ != Success).getOrElse(Success) }
  }

  def postQualifiedConversationMemberJoin(id: ConvId, members: Set[QualifiedId], defaultRole: ConversationRole): Future[SyncResult] =
    withConversation(id) { conv =>
      def post(users: Set[QualifiedId]) =
        conversationsClient.postQualifiedMemberJoin(conv.remoteId, users, defaultRole).future flatMap {
          case Left(resp @ ErrorResponse(status, _, label)) =>
            val errTpe = (status, label) match {
              case (403, "not-connected")             => Some(ErrorType.CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION)
              case (403, "too-many-members")          => Some(ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION)
              case (412, "missing-legalhold-consent") => Some(ErrorType.CANNOT_ADD_PARTICIPANT_WITH_MISSING_LEGAL_HOLD_CONSENT)
              case _                                  => None
            }
            convService
              .onMemberAddFailed(id, users.map(_.id), errTpe, resp)
              .map(_ => SyncResult(resp))
          case resp =>
            verbose(l"postConversationMemberJoin($id, $members, $defaultRole): $resp")
            postConvRespHandler(resp)
        }

      Future.traverse(members.grouped(PostMembersLimit))(post) map { _.find(_ != Success).getOrElse(Success) }
    }

  def postConversationMemberLeave(id: ConvId, user: UserId): Future[SyncResult] =
    if (user != selfUserId) postConv(id) { conv => conversationsClient.postMemberLeave(conv.remoteId, user) }
    else withConversation(id) { conv =>
      conversationsClient.postMemberLeave(conv.remoteId, user).future flatMap {
        case Right(Some(event: MemberLeaveEvent)) =>
          event.localTime = LocalInstant.Now
          conversationsClient.postConversationState(conv.remoteId, ConversationState(archived = Some(true), archiveTime = Some(event.time))).future flatMap {
            case Right(_) =>
              verbose(l"postConversationState finished")
              convEvents.handlePostConversationEvent(event)
                .map(_ => Success)
            case Left(error) =>
              Future.successful(SyncResult(error))
          }
        case Right(None) =>
          debug(l"member $user already left, just updating the conversation state")
          conversationsClient
            .postConversationState(conv.remoteId, ConversationState(archived = Some(true), archiveTime = Some(conv.lastEventTime)))
            .future
            .map(_ => Success)

        case Left(error) =>
          Future.successful(SyncResult(error))
      }
    }

  def postConversationState(id: ConvId, state: ConversationState): Future[SyncResult] =
    withConversation(id) { conv =>
      conversationsClient.postConversationState(conv.remoteId, state).map(SyncResult(_))
    }

  def postConversation(convId:      ConvId,
                       users:       Set[UserId],
                       name:        Option[Name],
                       team:        Option[TeamId],
                       access:      Set[Access],
                       accessRole:  AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole
                      ): Future[SyncResult] = {
    debug(l"postConversation($convId, $users, $name, $defaultRole)")
    val (toCreate, toAdd) = users.splitAt(PostMembersLimit)
    val initState = ConversationInitState(
      users                 = toCreate,
      name                  = name,
      team                  = team,
      access                = access,
      accessRole            = accessRole,
      receiptMode           = receiptMode,
      conversationRole      = defaultRole
    )
    conversationsClient.postConversation(initState).future.flatMap {
      case Right(response) =>
        convService.updateRemoteId(convId, response.id).flatMap { _ =>
          loadConversationRoles(Seq(response)).flatMap { roles =>
            convService.updateConversationsWithDeviceStartMessage(Seq(response), roles).flatMap { _ =>
              if (toAdd.nonEmpty) postConversationMemberJoin(convId, toAdd, defaultRole)
              else Future.successful(Success)
            }
          }
        }
      case Left(resp@ErrorResponse(status, _, label)) =>
        warn(l"got error: $resp")

        val errorType = (status, label) match {
          case (403, "not-connected") =>
            Some(ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER)
          case (412, "missing-legalhold-consent") =>
            Some(ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_USER_MISSING_LEGAL_HOLD_CONSENT)
          case _ =>
            None
        }

        errorType.fold(Future.successful(SyncResult(resp))) { errorType =>
          errorsService
            .addErrorWhenActive(ErrorData(errorType, resp, convId))
            .map(_ => SyncResult(resp))
        }
    }
  }


  def postQualifiedConversation(convId:      ConvId,
                                users:       Set[QualifiedId],
                                name:        Option[Name],
                                team:        Option[TeamId],
                                access:      Set[Access],
                                accessRole:  AccessRole,
                                receiptMode: Option[Int],
                                defaultRole: ConversationRole
                               ): Future[SyncResult] = {
    debug(l"postQualifiedConversation($convId, $users, $name, $defaultRole)")

    val initState = ConversationInitState(
      users                 = Set.empty, // TODO: for now we add all users after we created the conv, it will change in the future
      name                  = name,
      team                  = team,
      access                = access,
      accessRole            = accessRole,
      receiptMode           = receiptMode,
      conversationRole      = defaultRole
    )

    conversationsClient.postConversation(initState).future.flatMap {
      case Right(response) =>
        convService.updateRemoteId(convId, response.id).flatMap { _ =>
          loadConversationRoles(Seq(response)).flatMap { roles =>
            convService.updateConversationsWithDeviceStartMessage(Seq(response), roles).flatMap { _ =>
              postQualifiedConversationMemberJoin(convId, users, defaultRole)
            }
          }
        }

      case Left(resp@ErrorResponse(status, _, label)) =>
        warn(l"got error: $resp")

        val errorType = (status, label) match {
          case (403, "not-connected") =>
            Some(ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER)
          case (412, "missing-legalhold-consent") =>
            Some(ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_USER_MISSING_LEGAL_HOLD_CONSENT)
          case _ =>
            None
        }

        errorType.fold(Future.successful(SyncResult(resp))) { errorType =>
          errorsService
            .addErrorWhenActive(ErrorData(errorType, resp, convId))
            .map(_ => SyncResult(resp))
        }
    }
  }

  def syncConvLink(convId: ConvId): Future[SyncResult] = {
    (for {
      Some(conv) <- convs.convById(convId)
      resp       <- conversationsClient.getLink(conv.remoteId).future
      res        <- resp match {
        case Right(l)  => convStorage.update(conv.id, _.copy(link = l)).map(_ => Success)
        case Left(err) => Future.successful(SyncResult(err))
      }
    } yield res)
      .recover {
        case NonFatal(e) =>
          Retry("Failed to update conversation link")
      }
  }

  private def postConv(id: ConvId)(post: ConversationData => Future[Either[ErrorResponse, Option[ConversationEvent]]]): Future[SyncResult] =
    withConversation(id)(post(_).flatMap(postConvRespHandler))

  private val postConvRespHandler: (Either[ErrorResponse, Option[ConversationEvent]] => Future[SyncResult]) = {
    case Right(Some(event)) =>
      event.localTime = LocalInstant.Now
      convEvents
        .handlePostConversationEvent(event)
        .map(_ => Success)
    case Right(None) =>
      debug(l"postConv got success response, but no event")
      Future.successful(Success)
    case Left(error) => Future.successful(SyncResult(error))
  }

  private def withConversation(id: ConvId)(body: ConversationData => Future[SyncResult]): Future[SyncResult] =
    convs.convById(id) flatMap {
      case Some(conv) => body(conv)
      case _ =>
        Future.successful(Retry(s"No conversation found for id: $id")) // XXX: does it make sense to retry ?
    }
}
