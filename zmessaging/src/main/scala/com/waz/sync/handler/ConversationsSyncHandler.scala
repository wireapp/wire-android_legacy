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

import com.waz.ZLog.ImplicitTag._
import com.waz.log.ZLog2._
import com.waz.api.ErrorType
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.impl.ErrorResponse
import com.waz.content.{ConversationStorage, MessagesStorage}
import com.waz.model._
import com.waz.service._
import com.waz.service.assets.AssetService
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdaterImpl, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Retry, Success}
import com.waz.sync.client.ConversationsClient
import com.waz.sync.client.ConversationsClient.ConversationInitState
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future
import scala.util.control.NonFatal

object ConversationsSyncHandler {
  val PostMembersLimit = 256
}

class ConversationsSyncHandler(selfUserId:          UserId,
                               userService:         UserService,
                               messagesStorage:     MessagesStorage,
                               messagesService:     MessagesService,
                               convService:         ConversationsService,
                               convs:               ConversationsContentUpdaterImpl,
                               convEvents:          ConversationOrderEventsService,
                               convStorage:         ConversationStorage,
                               errorsService:       ErrorsService,
                               assetService:        AssetService,
                               conversationsClient: ConversationsClient,
                               genericMessages:     GenericMessageService) {

  import Threading.Implicits.Background
  import com.waz.sync.handler.ConversationsSyncHandler._
  private implicit val ec = EventContext.Global

  def syncConversations(ids: Seq[ConvId]): Future[SyncResult] =
    Future.sequence(ids.map(convs.convById)).flatMap { convs =>
      val remoteIds = convs.collect { case Some(conv) => conv.remoteId }

      if (remoteIds.size != convs.size) error(l"syncConversations($ids) - some conversations were not found in local db, skipping")

      conversationsClient.loadConversations(remoteIds).future flatMap {
        case Right(resps) =>
          debug(l"syncConversations received ${resps.size}")
          convService.updateConversationsWithDeviceStartMessage(resps).map(_ => Success)
        case Left(error) =>
          warn(l"ConversationsClient.syncConversations($ids) failed with error: $error")
          Future.successful(SyncResult(error))
      }
    }

  def syncConversations(start: Option[RConvId] = None): Future[SyncResult] =
    conversationsClient.loadConversations(start).future flatMap {
      case Right(ConversationsResult(convs, hasMore)) =>
        debug(l"syncConversations received ${convs.size}")
        val future = convService.updateConversationsWithDeviceStartMessage(convs)
        if (hasMore) syncConversations(convs.lastOption.map(_.id)).flatMap(res => future.map(_ => res))
        else future.map(_ => Success)
      case Left(error) =>
        warn(l"ConversationsClient.loadConversations($start) failed with error: $error")
        Future.successful(SyncResult(error))
    }

  def postConversationName(id: ConvId, name: Name): Future[SyncResult] =
    postConv(id) { conv => conversationsClient.postName(conv.remoteId, name).future }

  def postConversationReceiptMode(id: ConvId, receiptMode: Int): Future[SyncResult] =
    withConversation(id) { conv =>
      conversationsClient.postReceiptMode(conv.remoteId, receiptMode).map(SyncResult(_))
    }

  def postConversationMemberJoin(id: ConvId, members: Set[UserId]): Future[SyncResult] = withConversation(id) { conv =>
    def post(users: Set[UserId]) = conversationsClient.postMemberJoin(conv.remoteId, users).future flatMap {
      case Left(resp @ ErrorResponse(403, _, label)) =>
        val errTpe = label match {
          case "not-connected"    => Some(ErrorType.CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION)
          case "too-many-members" => Some(ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION)
          case _ => None
        }
        convService
          .onMemberAddFailed(id, users, errTpe, resp)
          .map(_ => SyncResult(resp))
      case resp =>
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

  def postConversation(convId: ConvId, users: Set[UserId], name: Option[Name], team: Option[TeamId], access: Set[Access], accessRole: AccessRole, receiptMode: Option[Int]): Future[SyncResult] = {
    debug(l"postConversation($convId, $users, $name)")
    val (toCreate, toAdd) = users.splitAt(PostMembersLimit)
    val initState = ConversationInitState(users = toCreate, name = name, team = team, access = access, accessRole = accessRole, receiptMode = receiptMode)
    conversationsClient.postConversation(initState).future.flatMap {
      case Right(response) =>
        convService.updateConversationsWithDeviceStartMessage(Seq(response)).flatMap { _ =>
          if (toAdd.nonEmpty) postConversationMemberJoin(convId, toAdd)
          else Future.successful(Success)
        }
      case Left(resp@ErrorResponse(403, msg, "not-connected")) =>
        warn(l"got error: $resp")
        errorsService
          .addErrorWhenActive(ErrorData(ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER, resp, convId))
          .map(_ => SyncResult(resp))
      case Left(error) =>
        Future.successful(SyncResult(error))
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
