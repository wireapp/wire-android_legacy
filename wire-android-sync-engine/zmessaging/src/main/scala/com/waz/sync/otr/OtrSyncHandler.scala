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
package com.waz.sync.otr

import com.waz.api.Verification
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.content.{ConversationStorage, MembersStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.conversation.ConversationsService
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent, MessageResponse}
import com.waz.sync.client._
import com.waz.utils.crypto.AESUtils

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.control.NonFatal

trait OtrSyncHandler {
  def postOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true): Future[Either[ErrorResponse, RemoteInstant]]
  def postSessionReset(convId: ConvId, user: UserId, client: ClientId): Future[SyncResult]
  def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]]
}

class OtrSyncHandlerImpl(teamId:             Option[TeamId],
                         selfClientId:       ClientId,
                         otrClient:          OtrClient,
                         msgClient:          MessagesClient,
                         service:            OtrService,
                         convsService:       ConversationsService,
                         convStorage:        ConversationStorage,
                         users:              UserService,
                         members:            MembersStorage,
                         errors:             ErrorsService,
                         clientsSyncHandler: OtrClientsSyncHandler,
                         push:               PushService,
                         usersStorage:       UsersStorage) extends OtrSyncHandler with DerivedLogTag {

  import OtrSyncHandler._
  import com.waz.threading.Threading.Implicits.Background

  override def postOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true) = {
    import com.waz.utils.{RichEither, RichFutureEither}

    def encryptAndSend(msg: GenericMessage, external: Option[Array[Byte]] = None, retries: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): ErrorOr[MessageResponse] =
      for {
        _          <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        _ = if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        us      <- recipients.fold(members.getActiveUsers(convId).map(_.toSet))(rs => Future.successful(rs))
        content <- service.encryptForUsers(us, msg, retries > 0, previous)
        resp    <-
          if (content.estimatedSize < MaxContentSize)
            msgClient.postMessage(conv.remoteId, OtrMessage(selfClientId, content, external, nativePush), ignoreMissing = retries > 1, recipients).future
          else {
            verbose(l"Message content too big, will post as External. Estimated size: ${content.estimatedSize}")
            val key = AESKey()
            val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(msg))
            val newMessage  = GenericMessage(Uid(msg.messageId), Proto.External(key, sha))
            encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
          }
        _ <- resp.map(_.deleted).mapFuture(service.deleteClients)
        _ <- resp.map(_.missing.keySet).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _))
        retry <- resp.flatMapFuture {
          case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
            clientsSyncHandler.syncSessions(missing).flatMap { err =>
              if (err.isDefined)
                error(l"syncSessions for missing clients failed: $err")
              encryptAndSend(msg, external, retries + 1, content)
            }
          case _: MessageResponse.Failure =>
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
          case resp => Future.successful(Right(resp))
        }
      } yield retry

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.hasCalling)
          errors.addConvUnverifiedError(convId, MessageId(message.messageId))

        Left(ErrorResponse.Unverified)
      case NonFatal(e) => Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }

  override def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]] =
    push.waitProcessing.flatMap { _ =>
      def broadcastRecipients = for {
        acceptedOrBlocked <- users.acceptedOrBlockedUsers.head
        myTeam <- teamId.fold(Future.successful(Set.empty[UserData]))(id => usersStorage.getByTeam(Set(id)))
        myTeamIds = myTeam.map(_.id)
      } yield acceptedOrBlocked.keySet ++ myTeamIds

      broadcastRecipients.flatMap { recp =>
        verbose(l"recipients: $recp")
        for {
          content <- service.encryptForUsers(recp, message, useFakeOnError = retry > 0, previous)
          r <- otrClient.broadcastMessage(OtrMessage(selfClientId, content), ignoreMissing = retry > 1, recp).future
          res <- loopIfMissingClients(r, retry, () => broadcastMessage(message, retry + 1, content))
        } yield res
      }
    }

  private def loopIfMissingClients(arg: Either[ErrorResponse, MessageResponse], retry: Int, fn: () => Future[Either[ErrorResponse, RemoteInstant]]): Future[Either[ErrorResponse, RemoteInstant]] =
    arg match {
      case Right(MessageResponse.Success(ClientMismatch(_, _, deleted, time))) =>
        // XXX: we are ignoring redundant clients, we rely on members list to encrypt messages, so if user left the conv then we won't use his clients on next message
        service.deleteClients(deleted).map(_ => Right(time))
      case Right(MessageResponse.Failure(ClientMismatch(_, missing, deleted, _))) =>
        service.deleteClients(deleted).flatMap { _ =>
          if (retry > 2)
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries: $missing")))
          else
            clientsSyncHandler.syncSessions(missing).flatMap {
              case None => fn()
              case Some(err) if retry < 3 =>
                error(l"syncSessions for missing clients failed: $err")
                fn()
              case Some(err) =>
                successful(Left(err))
            }
        }
      case Left(err) =>
        error(l"postOtrMessage failed with error: $err")
        successful(Left(err))
    }

  override def postSessionReset(convId: ConvId, user: UserId, client: ClientId) = {

    val msg = GenericMessage(Uid(), Proto.ClientAction.SessionReset)

    val convData = convStorage.get(convId).flatMap {
      case None => convStorage.get(ConvId(user.str))
      case conv => successful(conv)
    }

    def msgContent = service.encryptTargetedMessage(user, client, msg).flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        for {
          _       <- clientsSyncHandler.syncSessions(Map(user -> Seq(client)))
          content <- service.encryptTargetedMessage(user, client, msg)
        } yield content
    }

    convData.flatMap {
      case None =>
        successful(Failure(s"conv not found: $convId, for user: $user in postSessionReset"))
      case Some(conv) =>
        msgContent.flatMap {
          case None => successful(Failure(s"session not found for $user, $client"))
          case Some(content) =>
            msgClient
              .postMessage(conv.remoteId, OtrMessage(selfClientId, content), ignoreMissing = true).future
              .map(SyncResult(_))
        }
    }
  }
}

object OtrSyncHandler {

  case object UnverifiedException extends Exception

  case class OtrMessage(sender: ClientId, recipients: EncryptedContent, external: Option[Array[Byte]] = None, nativePush: Boolean = true)

  val MaxInlineSize  = 10 * 1024
  val MaxContentSize = 256 * 1024 // backend accepts 256KB for otr messages, but we would prefer to send less
}
