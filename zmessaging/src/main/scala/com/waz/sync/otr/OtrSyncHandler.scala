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
import com.waz.model.otr.{ClientId, ClientMismatch, MessageResponse, OtrClientIdMap, OtrMessage, QClientMismatch, QMessageResponse, QOtrClientIdMap, QualifiedOtrMessage}
import com.waz.service.conversation.ConversationsService
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.OtrClient.{EncryptedContent, QEncryptedContent}
import com.waz.sync.client._
import com.waz.sync.otr.OtrSyncHandler.{QTargetRecipients, TargetRecipients}
import com.waz.utils.crypto.AESUtils

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.control.NonFatal
import com.waz.utils.{RichEither, RichFutureEither}

trait OtrSyncHandler {
  def postOtrMessage(convId:                ConvId,
                     message:               GenericMessage,
                     isHidden:              Boolean,
                     targetRecipients:      TargetRecipients = TargetRecipients.ConversationParticipants,
                     nativePush:            Boolean = true,
                     enforceIgnoreMissing:  Boolean = false,
                     jobId:                 Option[SyncId] = None
                    ): ErrorOr[RemoteInstant]

  def postSessionReset(convId: ConvId, user: UserId, client: ClientId): Future[SyncResult]
  def broadcastMessage(message:    GenericMessage,
                       retry:      Int = 0,
                       previous:   EncryptedContent = EncryptedContent.Empty,
                       recipients: Option[Set[UserId]] = None
                      ): ErrorOr[RemoteInstant]
  def postClientDiscoveryMessage(convId: RConvId): ErrorOr[OtrClientIdMap]


  def postQualifiedOtrMessage(convId:                ConvId,
                              message:               GenericMessage,
                              isHidden:              Boolean,
                              targetRecipients:      QTargetRecipients = QTargetRecipients.ConversationParticipants,
                              nativePush:            Boolean = true,
                              enforceIgnoreMissing:  Boolean = false,
                              jobId:                 Option[SyncId] = None
                             ): ErrorOr[RemoteInstant]
  def postSessionReset(convId: ConvId, qualifiedId: QualifiedId, client: ClientId): Future[SyncResult]
  def postClientDiscoveryMessage(convId: RConvQualifiedId): ErrorOr[QOtrClientIdMap]
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
   * @param isHidden Whether the message is a hidden message. As a rule of thumb, if it doesn't
   *                 occupy a row in the message list, then it is hidden.
   * @param targetRecipients Who should receive the message.
   * @param nativePush
   * @param enforceIgnoreMissing When missing clients should be ignored.
   * @return
   */
  override def postOtrMessage(convId:                ConvId,
                              message:               GenericMessage,
                              isHidden:              Boolean,
                              targetRecipients:      TargetRecipients = TargetRecipients.ConversationParticipants,
                              nativePush:            Boolean = true,
                              enforceIgnoreMissing:  Boolean = false,
                              jobId:                 Option[SyncId] = None
                             ): ErrorOr[RemoteInstant] = {
    verbose(l"SSM14<JOB:$jobId> postMessage step 1")
    encryptAndSend(message, jobId = jobId)(convId, targetRecipients, EncryptAndSendFlags(isHidden, nativePush, enforceIgnoreMissing)).recover {
      case UnverifiedException =>
        verbose(l"SSM14<JOB:$jobId> postMessage step 2A")
        if (!message.proto.hasCalling) errors.addConvUnverifiedError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.Unverified)
      case LegalHoldDiscoveredException =>
        verbose(l"SSM14<JOB:$jobId> postMessage step 2B")
        errors.addUnapprovedLegalHoldStatusError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.UnapprovedLegalHold)
      case NonFatal(e) =>
        verbose(l"SSM14<JOB:$jobId> postMessage step 2C")
        Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight({
      verbose(l"SSM14<JOB:$jobId> postMessage step 3")
      _.mismatch.time
    })
  }

  /**
   * Post an otr message to a conversation.
   *
   * @param convId The id conversation to post the message to.
   * @param message The message being posted.
   * @param isHidden Whether the message is a hidden message. As a rule of thumb, if it doesn't
   *                 occupy a row in the message list, then it is hidden.
   @param targetRecipients Who should receive the message.
   * @param nativePush
   * @param enforceIgnoreMissing When missing clients should be ignored.
   * @return
   */
  override def postQualifiedOtrMessage(convId:                ConvId,
                                       message:               GenericMessage,
                                       isHidden:              Boolean,
                                       targetRecipients:      QTargetRecipients = QTargetRecipients.ConversationParticipants,
                                       nativePush:            Boolean = true,
                                       enforceIgnoreMissing:  Boolean = false,
                                       jobId:                 Option[SyncId] = None
                                      ): ErrorOr[RemoteInstant] = {
    verbose(l"SSM14<JOB:$jobId> postQualifiedMessage step 1")
    encryptAndSendQualified(message, jobId = jobId)(convId, targetRecipients, EncryptAndSendFlags(isHidden, nativePush, enforceIgnoreMissing)).recover {
      case UnverifiedException =>
        verbose(l"SSM14<JOB:$jobId> postQualifiedMessage step 2A")
        if (!message.proto.hasCalling) errors.addConvUnverifiedError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.Unverified)
      case LegalHoldDiscoveredException =>
        verbose(l"SSM14<JOB:$jobId> postQualifiedMessage step 2B")
        errors.addUnapprovedLegalHoldStatusError(convId, MessageId(message.proto.getMessageId))
        Left(ErrorResponse.UnapprovedLegalHold)
      case NonFatal(e) =>
        verbose(l"SSM14<JOB:$jobId> postQualifiedMessage step 2C")
        Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight({ r =>
      verbose(l"SSM14<JOB:$jobId> postQualifiedMessage step 3: ${r}")
      r.mismatch.time
    })
  }

  private def encryptAndSend(msg:      GenericMessage,
                             external: Option[Array[Byte]] = None,
                             retries:  Int = 0,
                             previous: EncryptedContent = EncryptedContent.Empty,
                             jobId:    Option[SyncId] = None
                            )
                            (implicit convId:           ConvId,
                                      targetRecipients: TargetRecipients,
                                      flags:            EncryptAndSendFlags): ErrorOr[MessageResponse] =
    for {
      _          <- push.waitProcessing(jobId)
      Some(conv) <- convStorage.get(convId)
      _          =  if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
      recipients <- clientsMap(targetRecipients, convId)
      content    <- service.encryptMessage(msg, recipients, retries > 0, previous)
      resp       <- if (external.isDefined || content.estimatedSize < MaxContentSize) {
                      val (shouldIgnoreMissingClients, targetUsers) = targetRecipients.missingClientsStrategy match {
                        case MissingClientsStrategy.DoNotIgnoreMissingClients                    => (false, None)
                        case MissingClientsStrategy.IgnoreMissingClientsExceptFromUsers(userIds) => (false, Some(userIds))
                        case MissingClientsStrategy.IgnoreMissingClients                         => (true, None)
                      }

                      msgClient.postMessage(
                        conv.remoteId,
                        OtrMessage(selfClientId, content, external, flags.nativePush, report_missing = targetUsers),
                        ignoreMissing = shouldIgnoreMissingClients || flags.enforceIgnoreMissing || retries > 1,
                        jobId
                      ).future
                    } else {
                      warn(l"Message content too big, will post as External. Estimated size: ${content.estimatedSize}")
                      val key = AESKey()
                      val (sha, data) = AESUtils.encrypt(key, msg.proto.toByteArray)
                      val newMessage  = GenericMessage(Uid(msg.proto.getMessageId), External(key, sha))
                      encryptAndSend(newMessage, external = Some(data), jobId = jobId) //abandon retries and previous EncryptedContent
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
                          needsConfirmation <- needsLegalHoldConfirmation(conv, flags.isHidden, missing.userIds)
                          _                  = if (needsConfirmation) throw LegalHoldDiscoveredException
                          result            <- encryptAndSend(msg, external = external, retries = retries + 1, previous = content, jobId = jobId)
                        } yield result
                      case _: MessageResponse.Failure =>
                        successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
                      case resp => Future.successful(Right(resp))
                    }
    } yield retry

  private def encryptAndSendQualified(msg:      GenericMessage,
                                      external: Option[Array[Byte]] = None,
                                      retries:  Int = 0,
                                      previous: QEncryptedContent = QEncryptedContent.Empty,
                                      jobId:    Option[SyncId] = None
                                     )
                                     (implicit convId:  ConvId,
                                      targetRecipients: QTargetRecipients,
                                      flags:            EncryptAndSendFlags
                                     ): ErrorOr[QMessageResponse] = {
    verbose(l"SSM17<JOB:$jobId> encryptAndSendQualified: beginning")
    for {
      _          <- push.waitProcessing(jobId)
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 1")})
      Some(conv) <- convStorage.get(convId)
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 2")})
      _          =  if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 3")})
      recipients <- clientsMap(targetRecipients, convId)
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 4")})
      content    <- service.encryptMessage(msg, recipients, retries > 0, previous)
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 5")})
      resp       <- if (external.isDefined || content.estimatedSize < MaxContentSize) {
        jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 6")})
        val (reportAll, targetUsers) = targetRecipients.missingClientsStrategy match {
          case QMissingClientsStrategy.DoNotIgnoreMissingClients                 => (!flags.enforceIgnoreMissing && retries == 0, None)
          case QMissingClientsStrategy.IgnoreMissingClientsExceptFromUsers(qIds) => (false, if (retries == 0) Some(qIds) else None)
          case QMissingClientsStrategy.IgnoreMissingClients                      => (false, None)
        }

        jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 7")})
        msgClient.postMessage(
          convsService.rConvQualifiedId(conv),
          QualifiedOtrMessage(selfClientId, content, external, flags.nativePush, reportMissing = targetUsers, reportAll = reportAll),
          jobId
        ).future
      } else {
        warn(l"Message content too big, will post as External. Job: $jobId. Estimated size: ${content.estimatedSize}")
        val key = AESKey()
        val (sha, data) = AESUtils.encrypt(key, msg.proto.toByteArray)
        val newMessage  = GenericMessage(Uid(msg.proto.getMessageId), External(key, sha))
        encryptAndSendQualified(newMessage, external = Some(data), jobId = jobId) //abandon retries and previous QEncryptedContent
      }
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 8")})
      _          <- resp.map(_.deleted).mapFuture(service.deleteClients)
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 9")})
      _          <- resp.map(_.missing.qualifiedIds.map(_.id)).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _))
      _          = jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 10")})
      retry      <- resp.flatMapFuture {
        case QMessageResponse.Failure(QClientMismatch(_, missing, _, _)) if retries < 3 =>
          warn(l"a message response failure with client mismatch with $missing, self client id is: $selfClientId, job: $jobId")
          for {
            syncResult        <- clientsSyncHandler.syncClients(missing.qualifiedIds)
            err                = SyncResult.unapply(syncResult)
            _                  = err.foreach { err => error(l"syncClients for missing clients failed: $err for job: $jobId") }
            needsConfirmation <- needsLegalHoldConfirmation(conv, flags.isHidden, missing.qualifiedIds.map(_.id))
            _                  = if (needsConfirmation) throw LegalHoldDiscoveredException
            result            <- encryptAndSendQualified(msg, external = external, retries = retries + 1, previous = content, jobId = jobId)
          } yield result
        case _: QMessageResponse.Failure =>
          successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries, job: $jobId")))
        case resp => {
          jobId.foreach({ j => verbose(l"SSM17<JOB:$j> encryptAndSendQualified: step 11")})
          Future.successful(Right(resp))
        }
      }
    } yield retry
  }


  private def needsLegalHoldConfirmation(conv: ConversationData, isMessageHidden: Boolean, usersWithMissingClients: Set[UserId]): Future[Boolean] =
    if (isMessageHidden || conv.isUnderLegalHold) Future.successful(false)
    else
      clientsStorage.getAll(usersWithMissingClients).map {
        _.exists {
          case Some(userClients) => userClients.containsLegalHoldDevice
          case None              => false
        }
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
      case TargetRecipients.ConversationParticipants       => members.getActiveUsers(convId).map(_.toSet).flatMap(clientIds)
      case TargetRecipients.SpecificUsers(userIds)         => clientIds(userIds)
      case TargetRecipients.SpecificClients(clientsByUser) => Future.successful(clientsByUser)
    }
  }

  private def clientsMap(targetRecipients: QTargetRecipients, convId: ConvId): Future[QOtrClientIdMap] = {
    def clientIds(qIds: Set[QualifiedId]): Future[QOtrClientIdMap] = {
      Future.traverse(qIds) { qId =>
        clientsStorage.getClients(qId.id).map { clients =>
          qId -> clients.map(_.id).filterNot(_ == selfClientId).toSet
        }
      }.map(QOtrClientIdMap(_))
    }

    targetRecipients match {
      case QTargetRecipients.ConversationParticipants       =>
        members.getActiveUsers(convId).flatMap(ids => userService.qualifiedIds(ids.toSet)).flatMap(clientIds)
      case QTargetRecipients.SpecificUsers(userIds)         => clientIds(userIds)
      case QTargetRecipients.SpecificClients(clientsByUser) => Future.successful(clientsByUser)
    }
  }

  private def getBroadcastRecipients =
    for {
      acceptedOrBlocked <- userService.acceptedOrBlockedUsers.head
      myTeam            <- teamId.fold(Future.successful(Set.empty[UserData]))(id => usersStorage.getByTeam(Set(id)))
      myTeamIds         =  myTeam.map(_.id)
    } yield acceptedOrBlocked.keySet ++ myTeamIds

  override def broadcastMessage(message:    GenericMessage,
                                retry:      Int = 0,
                                previous:   EncryptedContent = EncryptedContent.Empty,
                                recipients: Option[Set[UserId]] = None
                               ): Future[Either[ErrorResponse, RemoteInstant]] = push.waitProcessing(None).flatMap { _ =>
    val broadcastRecipients = recipients match {
      case Some(recp) => Future.successful(recp)
      case None       => getBroadcastRecipients
    }

    broadcastRecipients.flatMap { recp =>
      for {
        content  <- service.encryptMessageForUsers(message, recp, previous, useFakeOnError = retry > 0)
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

  private def loopIfMissingQualifiedClients(response:          Either[ErrorResponse, QMessageResponse],
                                            retry:             Int,
                                            currentRecipients: Set[QualifiedId],
                                            onRetry:           (Set[QualifiedId]) => Future[Either[ErrorResponse, RemoteInstant]]
                                           ): Future[Either[ErrorResponse, RemoteInstant]] =
    response match {
      case Right(QMessageResponse.Success(QClientMismatch(_, _, deleted, time))) =>
        // XXX: we are ignoring redundant clients, we rely on members list to encrypt messages,
        // so if user left the conv then we won't use his clients on next message
        service.deleteClients(deleted).map(_ => Right(time))
      case Right(QMessageResponse.Failure(QClientMismatch(_, missing, deleted, _))) =>
        service.deleteClients(deleted).flatMap {
          case _ if retry > 2 =>
            successful(Left(internalError(
              s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries: $missing"
            )))
          case _ =>
            clientsSyncHandler.syncClients(missing.qualifiedIds).flatMap { syncResult =>
              SyncResult.unapply(syncResult) match {
                case None                 => onRetry(missing.qualifiedIds)
                case Some(_) if retry < 3 => onRetry(currentRecipients)
                case Some(err)            => successful(Left(err))
              }
            }
        }
      case Left(err) =>
        error(l"postOtrMessage failed with error: $err")
        successful(Left(err))
    }

  private def getConvData(convId: ConvId, userId: UserId) = convStorage.get(convId).flatMap {
    case None => convStorage.get(ConvId(userId.str))
    case conv => successful(conv)
  }

  private def encrypt(userId: UserId, clientId: ClientId, msg: GenericMessage) =
    service.encryptTargetedMessage(userId, clientId, msg).flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        for {
          qId     <- userService.qualifiedId(userId)
          _       <- clientsSyncHandler.syncSessions(QOtrClientIdMap.from(qId -> Set(clientId)))
          content <- service.encryptTargetedMessage(userId, clientId, msg)
        } yield content
    }

  private def encrypt(qualifiedId: QualifiedId, clientId: ClientId, msg: GenericMessage) =
    service.encryptTargetedMessage(qualifiedId, clientId, msg).flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        for {
          _       <- clientsSyncHandler.syncSessions(QOtrClientIdMap.from(qualifiedId -> Set(clientId)))
          content <- service.encryptTargetedMessage(qualifiedId, clientId, msg)
        } yield content
    }

  override def postSessionReset(convId: ConvId, userId: UserId, clientId: ClientId): Future[SyncResult] =
    getConvData(convId, userId).flatMap {
      case None =>
        successful(Failure(s"conv not found: $convId, for user: $userId in postSessionReset"))
      case Some(conv) =>
        encrypt(userId, clientId, GenericMessage(Uid(), ClientAction.SessionReset)).flatMap {
          case None => successful(Failure(s"session not found for $userId, $clientId"))
          case Some(content) =>
            msgClient
              .postMessage(conv.remoteId, OtrMessage(selfClientId, content), ignoreMissing = true, None).future
              .map(SyncResult(_))
        }
    }

  override def postSessionReset(convId: ConvId, qualifiedId: QualifiedId, clientId: ClientId): Future[SyncResult] =
    getConvData(convId, qualifiedId.id).flatMap {
      case None =>
        successful(Failure(s"conv not found: $convId, for user: $qualifiedId in postSessionReset"))
      case Some(conv) =>
        encrypt(qualifiedId, clientId, GenericMessage(Uid(), ClientAction.SessionReset)).flatMap {
          case None => successful(Failure(s"session not found for $qualifiedId, $clientId"))
          case Some(content) =>
            msgClient
              .postMessage(
                convsService.rConvQualifiedId(conv),
                QualifiedOtrMessage(selfClientId, content),
                None
              )
              .future
              .map(SyncResult(_))
        }
    }

  override def postClientDiscoveryMessage(convId: RConvId): ErrorOr[OtrClientIdMap] =
    for {
      Some(conv) <- convStorage.getByRemoteId(convId)
      message    =  OtrMessage(selfClientId, EncryptedContent.Empty, nativePush = false)
      response   <- msgClient.postMessage(conv.remoteId, message, ignoreMissing = false, None).future
    } yield response match {
      case Left(error) => Left(error)
      case Right(resp) => Right(resp.missing)
    }

  override def postClientDiscoveryMessage(convId: RConvQualifiedId): ErrorOr[QOtrClientIdMap] = {
    val message = QualifiedOtrMessage(selfClientId, QEncryptedContent.Empty, nativePush = false)
    msgClient.postMessage(convId, message, None).future.map {
      case Left(error) => Left(error)
      case Right(resp) => Right(resp.missing)
    }
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

  final case class EncryptAndSendFlags(isHidden: Boolean, nativePush: Boolean, enforceIgnoreMissing: Boolean)

  val MaxInlineSize  = 10 * 1024
  val MaxContentSize = 256 * 1024 // backend accepts 256KB for otr messages, but we would prefer to send less

  /// Describes who should receive a message.
  sealed trait TargetRecipients {
    import TargetRecipients._
    import MissingClientsStrategy._
    def missingClientsStrategy: MissingClientsStrategy =
      this match {
        case ConversationParticipants => DoNotIgnoreMissingClients
        case SpecificUsers(userIds)   => IgnoreMissingClientsExceptFromUsers(userIds)
        case SpecificClients(_)       => IgnoreMissingClients
      }
  }

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

  /// Describes who should receive a message.
  sealed trait QTargetRecipients {
    import QTargetRecipients._
    import QMissingClientsStrategy._
    def missingClientsStrategy: QMissingClientsStrategy =
      this match {
        case ConversationParticipants => DoNotIgnoreMissingClients
        case SpecificUsers(userIds)   => IgnoreMissingClientsExceptFromUsers(userIds)
        case SpecificClients(_)       => IgnoreMissingClients
      }
  }

  object QTargetRecipients {
    /// All participants (and all their clients) should receive the message.
    final object ConversationParticipants extends QTargetRecipients

    /// All clients of the given users should receive the message.
    final case class SpecificUsers(qualifiedIds: Set[QualifiedId]) extends QTargetRecipients

    /// These exact clients should receive the message.
    final case class SpecificClients(clientsByUser: QOtrClientIdMap) extends QTargetRecipients
  }

  /// Describes how missing clients should be handled.
  sealed trait QMissingClientsStrategy

  object QMissingClientsStrategy {
    /// Fetch missing clients and resend the message.
    object DoNotIgnoreMissingClients extends QMissingClientsStrategy

    /// Send the message without fetching missing clients.
    object IgnoreMissingClients extends QMissingClientsStrategy

    /// Only fetch missing clients from the given users and resend the message.
    final case class IgnoreMissingClientsExceptFromUsers(qualifiedIds: Set[QualifiedId]) extends QMissingClientsStrategy
  }
}
