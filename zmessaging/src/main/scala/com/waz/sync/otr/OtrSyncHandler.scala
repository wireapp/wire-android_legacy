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
import com.waz.content.{ConversationStorage, MembersStorage, OtrClientsStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.{error, _}
import com.waz.model.GenericContent.{ClientAction, External}
import com.waz.model._
import com.waz.model.otr.{ClientId, OtrClientIdMap, OtrMessage, QOtrClientIdMap}
import com.waz.service.conversation.ConversationsService
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent, MessageResponse}
import com.waz.sync.client._
import com.waz.sync.otr.OtrSyncHandler.MissingClientsStrategy._
import com.waz.sync.otr.OtrSyncHandler.TargetRecipients
import com.waz.sync.otr.OtrSyncHandler.TargetRecipients._
import com.waz.utils.crypto.AESUtils

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.control.NonFatal

trait OtrSyncHandler {
  def postOtrMessage(convId:                ConvId,
                     message:               GenericMessage,
                     targetRecipients:      TargetRecipients = ConversationParticipants,
                     isHidden:              Boolean,
                     nativePush:            Boolean = true,
                     enforceIgnoreMissing:  Boolean = false
                    ): ErrorOr[RemoteInstant]

  def postSessionReset(convId: ConvId, user: UserId, client: ClientId): Future[SyncResult]
  def broadcastMessage(message:    GenericMessage,
                       retry:      Int = 0,
                       previous:   EncryptedContent = EncryptedContent.Empty,
                       recipients: Option[Set[UserId]] = None
                      ): ErrorOr[RemoteInstant]
  def postClientDiscoveryMessage(convId: RConvId): ErrorOr[OtrClientIdMap]
}

class OtrSyncHandlerImpl(teamId:             Option[TeamId],
                         selfClientId:       ClientId,
                         otrClient:          OtrClient,
                         msgClient:          MessagesClient,
                         service:            OtrService,
                         convsService:       ConversationsService,
                         convStorage:        ConversationStorage,
                         userService:        UserService,
                         members:            MembersStorage,
                         errors:             ErrorsService,
                         clientsSyncHandler: OtrClientsSyncHandler,
                         push:               PushService,
                         usersStorage:       UsersStorage,
                         clientsStorage:     OtrClientsStorage) extends OtrSyncHandler with DerivedLogTag {

  import OtrSyncHandler._
  import com.waz.threading.Threading.Implicits.Background

  /**
   * Post an otr message to a conversation.
   *
   * @param convId The id conversation to post the message to.
   * @param message The message being posted.
   * @param targetRecipients Who should receive the message.
   * @param isHidden Whether the message is a hidden message. As a rule of thumb, if it doesn't
   *                 occupy a row in the message list, then it is hidden.
   * @param nativePush
   * @param enforceIgnoreMissing When missing clients should be ignored.
   * @return
   */
  override def postOtrMessage(convId:                ConvId,
                              message:               GenericMessage,
                              targetRecipients:      TargetRecipients = ConversationParticipants,
                              isHidden:              Boolean,
                              nativePush:            Boolean = true,
                              enforceIgnoreMissing:  Boolean = false
                             ): ErrorOr[RemoteInstant] = {
    import com.waz.utils.{RichEither, RichFutureEither}

    def encryptAndSend(msg:      GenericMessage,
                       external: Option[Array[Byte]] = None,
                       retries:  Int = 0,
                       previous: EncryptedContent = EncryptedContent.Empty
                      ): ErrorOr[MessageResponse] =
      for {
        _          <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        _          =  if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        recipients <- clientsMap(targetRecipients, convId)
        content    <- service.encryptMessage(msg, recipients, retries > 0, previous)
        resp       <- if (external.isDefined || content.estimatedSize < MaxContentSize) {
                        val (shouldIgnoreMissingClients, targetUsers) = missingClientsStrategy(targetRecipients) match {
                          case DoNotIgnoreMissingClients                    => (false, None)
                          case IgnoreMissingClientsExceptFromUsers(userIds) => (false, Some(userIds))
                          case IgnoreMissingClients                         => (true, None)
                        }

                        msgClient.postMessage(
                          conv.remoteId,
                          OtrMessage(selfClientId, content, external, nativePush, report_missing = targetUsers),
                          ignoreMissing = shouldIgnoreMissingClients || enforceIgnoreMissing || retries > 1
                        ).future
                      } else {
                        warn(l"Message content too big, will post as External. Estimated size: ${content.estimatedSize}")
                        val key = AESKey()
                        val (sha, data) = AESUtils.encrypt(key, msg.proto.toByteArray)
                        val newMessage  = GenericMessage(Uid(msg.proto.getMessageId), External(key, sha))
                        encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
                      }
        _          <- resp.map(_.deleted).mapFuture(service.deleteClients)
        _          <- resp.map(_.missing.userIds).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _))
        retry      <- resp.flatMapFuture {
                        case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
                          warn(l"a message response failure with client mismatch with $missing, self client id is: $selfClientId")
                          for {
                            syncResult        <- syncClients(missing.userIds)
                            err                = SyncResult.unapply(syncResult)
                            _                  = err.foreach { err => error(l"syncClients for missing clients failed: $err") }
                            needsConfirmation <- needsLegalHoldConfirmation(conv, isHidden, missing.userIds)
                            _                  = if (needsConfirmation) throw LegalHoldDiscoveredException
                            result            <- encryptAndSend(msg, external, retries + 1, content)
                          } yield result
                        case _: MessageResponse.Failure =>
                          successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
                        case resp => Future.successful(Right(resp))
                      }
      } yield retry

    def needsLegalHoldConfirmation(conv: ConversationData,
                                   isMessageHidden: Boolean,
                                   usersWithMissingClients: Set[UserId]): Future[Boolean] = {
      if (isMessageHidden || conv.isUnderLegalHold) {
        Future.successful(false)
      } else {
        clientsStorage.getAll(usersWithMissingClients).map {
          _.exists {
            case Some(userClients) => userClients.containsLegalHoldDevice
            case None => false
          }
        }
      }
    }

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.proto.hasCalling) errors.addConvUnverifiedError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.Unverified)
      case LegalHoldDiscoveredException =>
        errors.addUnapprovedLegalHoldStatusError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.UnapprovedLegalHold)
      case NonFatal(e) =>
        Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }

  private def missingClientsStrategy(targetRecipients: TargetRecipients): MissingClientsStrategy =
    targetRecipients match {
      case ConversationParticipants => DoNotIgnoreMissingClients
      case SpecificUsers(userIds)   => IgnoreMissingClientsExceptFromUsers(userIds)
      case SpecificClients(_)       => IgnoreMissingClients
    }

  private def clientsMap(targetRecipients: TargetRecipients, convId: ConvId): Future[OtrClientIdMap] = {
    def clientIds(userIds: Set[UserId]): Future[OtrClientIdMap] = {
      Future.traverse(userIds) { userId =>
        clientsStorage.getClients(userId).map { clients =>
          userId -> clients.map(_.id).filterNot(_ == selfClientId).toSet
        }
      }.map(OtrClientIdMap(_))
    }

    targetRecipients match {
      case ConversationParticipants       => members.getActiveUsers(convId).map(_.toSet).flatMap(clientIds)
      case SpecificUsers(userIds)         => clientIds(userIds)
      case SpecificClients(clientsByUser) => Future.successful(clientsByUser)
    }
  }

  override def broadcastMessage(message:    GenericMessage,
                                retry:      Int = 0,
                                previous:   EncryptedContent = EncryptedContent.Empty,
                                recipients: Option[Set[UserId]] = None
                               ): Future[Either[ErrorResponse, RemoteInstant]] =
    push.waitProcessing.flatMap { _ =>
      val broadcastRecipients = recipients match {
        case Some(recp) => Future.successful(recp)
        case None =>
          for {
            acceptedOrBlocked <- userService.acceptedOrBlockedUsers.head
            myTeam            <- teamId.fold(Future.successful(Set.empty[UserData]))(id => usersStorage.getByTeam(Set(id)))
            myTeamIds         =  myTeam.map(_.id)
          } yield acceptedOrBlocked.keySet ++ myTeamIds
      }

      import com.waz.utils.RichEither

      broadcastRecipients.flatMap { recp =>
        for {
          content  <- service.encryptMessageForUsers(message, recp, useFakeOnError = retry > 0, previous)
          response <- otrClient.broadcastMessage(
                        OtrMessage(selfClientId, content, report_missing = Some(recp)),
                        ignoreMissing = retry > 1
                      ).future
          _        <- response.map(_.deleted).mapFuture(service.deleteClients)
          res      <- loopIfMissingClients(
                       response,
                       retry,
                       recp,
                       (rs: Set[UserId]) => broadcastMessage(message, retry + 1, content, Some(rs))
                      )
        } yield res
      }
    }

  private def loopIfMissingClients(response:          Either[ErrorResponse, MessageResponse],
                                   retry:             Int,
                                   currentRecipients: Set[UserId],
                                   onRetry:           (Set[UserId]) => Future[Either[ErrorResponse, RemoteInstant]]
                                  ): Future[Either[ErrorResponse, RemoteInstant]] =
    response match {
      case Right(MessageResponse.Success(ClientMismatch(_, _, deleted, time))) =>
        // XXX: we are ignoring redundant clients, we rely on members list to encrypt messages,
        // so if user left the conv then we won't use his clients on next message
        service.deleteClients(deleted).map(_ => Right(time))
      case Right(MessageResponse.Failure(ClientMismatch(_, missing, deleted, _))) =>
        service.deleteClients(deleted).flatMap {
          case _ if retry > 2 =>
            successful(Left(internalError(
              s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries: $missing"
            )))
          case _ =>
            syncClients(missing.userIds).flatMap { syncResult =>
              SyncResult.unapply(syncResult) match {
                case None                 => onRetry(missing.userIds)
                case Some(_) if retry < 3 => onRetry(currentRecipients)
                case Some(err)            => successful(Left(err))
              }
            }
        }
      case Left(err) =>
        error(l"postOtrMessage failed with error: $err")
        successful(Left(err))
    }

  override def postSessionReset(convId: ConvId, userId: UserId, clientId: ClientId): Future[SyncResult] = {

    val msg = GenericMessage(Uid(), ClientAction.SessionReset)

    val convData = convStorage.get(convId).flatMap {
      case None => convStorage.get(ConvId(userId.str))
      case conv => successful(conv)
    }

    def msgContent = service.encryptTargetedMessage(userId, clientId, msg).flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        for {
          qId     <- userService.qualifiedId(userId)
          _       <- clientsSyncHandler.syncSessions(QOtrClientIdMap.from(qId -> Set(clientId)))
          content <- service.encryptTargetedMessage(userId, clientId, msg)
        } yield content
    }

    convData.flatMap {
      case None =>
        successful(Failure(s"conv not found: $convId, for user: $userId in postSessionReset"))
      case Some(conv) =>
        msgContent.flatMap {
          case None => successful(Failure(s"session not found for $userId, $clientId"))
          case Some(content) =>
            msgClient
              .postMessage(conv.remoteId, OtrMessage(selfClientId, content), ignoreMissing = true).future
              .map(SyncResult(_))
        }
    }
  }

  override def postClientDiscoveryMessage(convId: RConvId): ErrorOr[OtrClientIdMap] =
    for {
      Some(conv) <- convStorage.getByRemoteId(convId)
      message    =  OtrMessage(selfClientId, EncryptedContent.Empty, nativePush = false)
      response   <- msgClient.postMessage(conv.remoteId, message, ignoreMissing = false).future
    } yield response match {
      case Left(error) => Left(error)
      case Right(resp) => Right(resp.missing)
    }

  private def syncClients(users: Set[UserId]): Future[SyncResult] =
    for {
      qIds   <- userService.qualifiedIds(users)
      result <- clientsSyncHandler.syncClients(qIds)
    } yield result
}

object OtrSyncHandler {

  final case object UnverifiedException extends Exception
  final case object LegalHoldDiscoveredException extends Exception

  val MaxInlineSize  = 10 * 1024
  val MaxContentSize = 256 * 1024 // backend accepts 256KB for otr messages, but we would prefer to send less

  /// Describes who should receive a message.
  sealed trait TargetRecipients

  object TargetRecipients {
    /// All participants (and all their clients) should receive the message.
    final object ConversationParticipants extends TargetRecipients

    /// All clients of the given users should receive the message.
    final case class SpecificUsers(userIds: Set[UserId]) extends TargetRecipients

    /// These exact clients should receive the message.
    final case class SpecificClients(clientsByUser: OtrClientIdMap) extends TargetRecipients
  }

  /// Describes how missing clients should be handled.
  sealed trait MissingClientsStrategy

  object MissingClientsStrategy {
    /// Fetch missing clients and resend the message.
    object DoNotIgnoreMissingClients extends MissingClientsStrategy

    /// Send the message without fetching missing clients.
    object IgnoreMissingClients extends MissingClientsStrategy

    /// Only fetch missing clients from the given users and resend the message.
    final case class IgnoreMissingClientsExceptFromUsers(userIds: Set[UserId]) extends MissingClientsStrategy
  }

}
