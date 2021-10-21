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
import com.waz.model.{RConvQualifiedId, _}
import com.waz.service._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Retry, Success}
import com.waz.sync.client.{ConversationsClient, ErrorOr}
import com.waz.sync.client.ConversationsClient.ConversationResponse.{ConversationsResult, QConversationsResult}
import com.waz.sync.client.ConversationsClient.{ConversationInitState, ConversationResponse}
import com.waz.threading.Threading
import com.waz.zms.BuildConfig

import scala.concurrent.Future
import scala.util.Right
import scala.util.control.NonFatal

object ConversationsSyncHandler {
  val PostMembersLimit = 256
}

class ConversationsSyncHandler(selfUserId:      UserId,
                               selfDomain:      Domain,
                               teamId:          Option[TeamId],
                               userService:     UserService,
                               messagesStorage: MessagesStorage,
                               messagesService: MessagesService,
                               convService:     ConversationsService,
                               convUpdater:     ConversationsContentUpdater,
                               convEvents:      ConversationOrderEventsService,
                               convStorage:     ConversationStorage,
                               errorsService:   ErrorsService,
                               convClient:      ConversationsClient,
                               genericMessages: GenericMessageService,
                               rolesService:    ConversationRolesService,
                               membersStorage:  MembersStorage
                              ) extends DerivedLogTag {

  import Threading.Implicits.Background
  import com.waz.sync.handler.ConversationsSyncHandler._

  // optimization: same team conversations and private conversations use default roles so we don't have to ask the backend
  private def loadConversationRoles(resps: Seq[ConversationResponse]) = {
    val (otherTeamResps, teamAndPrivResps) = resps.partition(r => r.team.isDefined && r.team != teamId)
    rolesService.defaultRoles.head.flatMap { defRoles =>
      // @todo: for now we have no way to check conversation roles on a federated backend
      val convIds = otherTeamResps.filterNot(_.hasDomain).map(_.id).toSet
      convClient
        .loadConversationRoles(convIds, defRoles)
        .map(_ ++ teamAndPrivResps.map(r => r.id -> defRoles).toMap)
    }
  }

  def syncConversations(ids: Set[ConvId]): Future[SyncResult] =
    convStorage.getAll(ids).flatMap { convs =>
      val load: ErrorOr[Seq[ConversationResponse]] =
        if (BuildConfig.FEDERATION_USER_DISCOVERY) {
          val qIds = convs.flatMap {
            case Some(conv) => Option(convService.rConvQualifiedId(conv))
            case None =>
              error(l"syncConversations($ids) - a conversation was not found in local db, skipping")
              None
          }

          if (qIds.nonEmpty)
            convClient.loadQualifiedConversations(qIds.toSet).future.map {
              case Left(error) => Left(error)
              case Right(qrs)  => Right(qrs.found)
            }
          else
            Future.successful(Right(Seq.empty[ConversationResponse]))
        } else {
          val remoteIds = convs.collect { case Some(conv) => conv.remoteId }.toSet
          if (remoteIds.size != convs.size)
            error(l"syncConversations($ids) - some conversations were not found in local db, skipping")
          convClient.loadConversations(remoteIds).future
        }

      load.flatMap {
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

  def syncConversations(): Future[SyncResult] =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      syncQualifiedConversations(None, Set.empty)
    } else {
      syncConversations(None, Set.empty)
    }


  private def syncQualifiedConversations(pagingState: Option[String], qIds: Set[RConvQualifiedId]): Future[SyncResult] =
    convClient.loadQualifiedConversationsIds(pagingState).future.flatMap {
      case Right(resp) =>
        convClient.loadQualifiedConversations(resp.qIds).future.flatMap {
          case Right(QConversationsResult(found, missing, _)) =>
            for {
              roles      <- loadConversationRoles(found)
              _          <- convService.updateConversationsWithDeviceStartMessage(found, roles)
              _          <- if (missing.nonEmpty) removeConvsMissingOnBackend(missing.map(_.id))                  else Future.successful(())
              _          <- if (!resp.hasMore)    removeConvsMissingOnBackend((resp.qIds ++ qIds).map(_.id))      else Future.successful(())
              syncResult <- if (resp.hasMore)     syncQualifiedConversations(resp.pagingState, resp.qIds ++ qIds) else Future.successful(Success)
            } yield syncResult
          case Left(error) =>
            Future.successful(SyncResult(error))
        }
      case Left(error) =>
        Future.successful(SyncResult(error))
    }

  private def syncConversations(start: Option[RConvId], rIdsFromBackend: Set[RConvId]): Future[SyncResult] =
    convClient.loadConversations(start).future.flatMap {
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
    postConv(id) { conv => convClient.postName(conv.remoteId, name).future }

  def postConversationReceiptMode(id: ConvId, receiptMode: Int): Future[SyncResult] =
    withConversation(id) { conv =>
      convClient.postReceiptMode(conv.remoteId, receiptMode).map(SyncResult(_))
    }

  def postConversationRole(id: ConvId, userId: UserId, newRole: ConversationRole, origRole: ConversationRole): Future[SyncResult] =
    withConversation(id) { conv =>
      convClient.postConversationRole(conv.remoteId, userId, newRole).future.flatMap {
        case Right(_) =>
          Future.successful(Success)
        case Left(error) =>
          convService.onUpdateRoleFailed(id, userId, newRole, origRole, error).map(_ => SyncResult(error))
      }
    }

  private def handleMemberJoinResponse(id: ConvId, users: Set[UserId], response: Either[ErrorResponse, Option[MemberJoinEvent]]) =
    response match {
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
        postConvRespHandler(resp)
    }

  def postConversationMemberJoin(id: ConvId, members: Set[UserId], defaultRole: ConversationRole): Future[SyncResult] =
    withConversation(id) { conv =>
      val grouped = members.grouped(PostMembersLimit)
      for {
        responses   <- Future.traverse(grouped)(convClient.postMemberJoin(conv.remoteId, _, defaultRole).future)
        syncResults <- Future.traverse(responses)(handleMemberJoinResponse(id, members, _))
      } yield syncResults.find(_ != Success).getOrElse(Success)
    }

  def postQualifiedConversationMemberJoin(id: ConvId, members: Set[QualifiedId], defaultRole: ConversationRole): Future[SyncResult] =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      withConversation(id) { conv =>
        val grouped = members.grouped(PostMembersLimit)
        for {
          responses   <- Future.traverse(grouped)(convClient.postQualifiedMemberJoin(conv.remoteId, _, defaultRole).future)
          syncResults <- Future.traverse(responses)(handleMemberJoinResponse(id, members.map(_.id), _))
        } yield syncResults.find(_ != Success).getOrElse(Success)
      }
    } else {
      postConversationMemberJoin(id, members.map(_.id), defaultRole)
    }

  private def postSelfLeave(id: ConvId): Future[SyncResult] =
    withConversation(id) { conv =>
      val clientResult =
        (BuildConfig.FEDERATION_USER_DISCOVERY, selfDomain.isDefined) match {
          case (true, true) =>
            val convQualifiedId = convService.rConvQualifiedId(conv)
            val userQualifiedId = QualifiedId(selfUserId, selfDomain)
            convClient.postQualifiedMemberLeave(convQualifiedId, userQualifiedId)
          case _ =>
            convClient.postMemberLeave(conv.remoteId, selfUserId)
        }
      clientResult.future.flatMap {
        case Right(Some(event: MemberLeaveEvent)) =>
          event.localTime = LocalInstant.Now
          convClient.postConversationState(conv.remoteId, ConversationState(archived = Some(true), archiveTime = Some(event.time))).future flatMap {
            case Right(_) =>
              verbose(l"postConversationState finished")
              convEvents.handlePostConversationEvent(event)
                .map(_ => Success)
            case Left(error) =>
              Future.successful(SyncResult(error))
          }
        case Right(None) =>
          debug(l"member self already left, just updating the conversation state")
          convClient
            .postConversationState(conv.remoteId, ConversationState(archived = Some(true), archiveTime = Some(conv.lastEventTime)))
            .future
            .map(_ => Success)

        case Left(error) =>
          Future.successful(SyncResult(error))
      }
    }

  def postConversationMemberLeave(id: ConvId, user: UserId): Future[SyncResult] =
    if (user != selfUserId)
      postConv(id) { conv => convClient.postMemberLeave(conv.remoteId, user) }
    else
      postSelfLeave(id)

  def postConversationMemberLeave(convId: ConvId, qId: QualifiedId): Future[SyncResult] =
    if (BuildConfig.FEDERATION_USER_DISCOVERY && qId.id != selfUserId) {
      postConv(convId) { conv =>
        val rConvId = convService.rConvQualifiedId(conv)
        convClient.postQualifiedMemberLeave(rConvId, qId)
      }
    } else {
      postConversationMemberLeave(convId, qId.id)
    }

  def postConversationState(id: ConvId, state: ConversationState): Future[SyncResult] =
    withConversation(id) { conv =>
      convClient.postConversationState(conv.remoteId, state).map(SyncResult(_))
    }

  def postConversation(convId:      ConvId,
                       otherUser:   Option[UserId],
                       name:        Option[Name],
                       team:        Option[TeamId],
                       access:      Set[Access],
                       accessRole:  AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole
                      ): Future[SyncResult] = {
    debug(l"postConversation($convId, $otherUser, $name, $defaultRole)")
    val initState =
      ConversationInitState(
        users                 = otherUser.map(Set(_)).getOrElse(Set.empty),
        qualifiedUsers        = Set.empty,
        name                  = name,
        team                  = team,
        access                = access,
        accessRole            = accessRole,
        receiptMode           = receiptMode,
        conversationRole      = defaultRole
      )
    convClient.postConversation(initState).future.flatMap(handlePostConvResponse(convId, _))
  }

  def postConversation(convId:      ConvId,
                       otherUser:   QualifiedId,
                       team:        Option[TeamId],
                       access:      Set[Access],
                       accessRole:  AccessRole,
                       receiptMode: Option[Int],
                       defaultRole: ConversationRole
                      ): Future[SyncResult] = {
    debug(l"postConversation($convId, $otherUser, $defaultRole)")
    val initState =
      ConversationInitState(
        users                 = Set.empty,
        qualifiedUsers        = Set(otherUser),
        name                  = None,
        team                  = team,
        access                = access,
        accessRole            = accessRole,
        receiptMode           = receiptMode,
        conversationRole      = defaultRole
      )
    convClient.postConversation(initState).future.flatMap(handlePostConvResponse(convId, _))
  }

  private def handlePostConvResponse(convId: ConvId, resp: Either[ErrorResponse, ConversationResponse]) = resp match {
    case Right(response) =>
      for {
        _     <- convService.updateRemoteId(convId, response.id)
        roles <- loadConversationRoles(Seq(response))
        _     <- convService.updateConversationsWithDeviceStartMessage(Seq(response), roles)
      } yield Success
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

  def syncConvLink(convId: ConvId): Future[SyncResult] = {
    (for {
      Some(conv) <- convUpdater.convById(convId)
      resp       <- convClient.getLink(conv.remoteId).future
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
    convStorage.get(id).flatMap {
      case Some(conv) => body(conv)
      case _          => Future.successful(Retry(s"No conversation found for id: $id")) // XXX: does it make sense to retry ?
    }
}
