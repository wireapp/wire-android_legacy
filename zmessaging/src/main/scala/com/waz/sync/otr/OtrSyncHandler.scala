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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Verification
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.cache.{CacheService, LocalData}
import com.waz.content.{ConversationStorage, UsersStorage}
import com.waz.model.AssetData.RemoteData
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsService
import com.waz.service.messages.MessagesService
import com.waz.service.otr.{OtrClientsService, OtrService}
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.client.AssetClient.{Metadata, Retention, UploadResponse}
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent, MessageResponse}
import com.waz.sync.client.{AssetClient, MessagesClient, OtrClient, UsersClient}
import com.waz.threading.CancellableFuture
import com.waz.utils.crypto.AESUtils
import com.waz.sync.client.ErrorOrResponse

import scala.concurrent.Future
import scala.concurrent.Future.successful

trait OtrSyncHandler {
  def postOtrMessage(conv: ConversationData, message: GenericMessage): Future[Either[ErrorResponse, RemoteInstant]]
  def postOtrMessage(convId: ConvId, remoteId: RConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true): Future[Either[ErrorResponse, RemoteInstant]]
  def uploadAssetDataV3(data: LocalData, key: Option[AESKey], mime: Mime = Mime.Default, retention: Retention): CancellableFuture[Either[ErrorResponse, RemoteData]]
  def postSessionReset(convId: ConvId, user: UserId, client: ClientId): Future[SyncResult]
  def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]]
}

class OtrSyncHandlerImpl(teamId:             Option[TeamId],
                         otrClient:          OtrClient,
                         msgClient:          MessagesClient,
                         assetClient:        AssetClient,
                         service:            OtrService,
                         clients:            OtrClientsService,
                         assets:             AssetService,
                         convs:              ConversationsService,
                         convStorage:        ConversationStorage,
                         users:              UserService,
                         messages:           MessagesService,
                         errors:             ErrorsService,
                         clientsSyncHandler: OtrClientsSyncHandler,
                         cache:              CacheService,
                         push:               PushService,
                         usersClient:        UsersClient,
                         usersStorage:       UsersStorage) extends OtrSyncHandler {

  import OtrSyncHandler._
  import com.waz.threading.Threading.Implicits.Background

  def postOtrMessage(conv: ConversationData, message: GenericMessage) = postOtrMessage(conv.id, conv.remoteId, message)

  def postOtrMessage(convId: ConvId, remoteId: RConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true) = push.afterProcessing {
    clients.getSelfClient flatMap {
      case Some(selfClient) =>
        encryptMessageForClients(convId, message, recipients = recipients) {
          case (content, retry) if content.estimatedSize < MaxContentSize =>
            msgClient.postMessage(remoteId, OtrMessage(selfClient.id, content, nativePush = nativePush), ignoreMissing = retry > 1, recipients)
          case (content, _) =>
            verbose(s"Message content too big, will post as External. Estimated size: ${content.estimatedSize}")
            postExternalMessage(selfClient.id, convId, remoteId, message, recipients, nativePush)
        }
      case None =>
        successful(Left(internalError("Client is not registered")))
    }

  }

  // will retry 3 times, at first we try to send message in normal way,
  // when it fails we will try sending empty messages to contacts for which we can not encrypt the message
  // in last try we will use 'ignore_missing' flag
  private def encryptMessageForClients(convId: ConvId, message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty, recipients: Option[Set[UserId]] = None)
                                      (f: (EncryptedContent, Int) => ErrorOrResponse[MessageResponse]): Future[Either[ErrorResponse, RemoteInstant]] = convStorage.get(convId) flatMap {
    case Some(conv) if conv.verified == Verification.UNVERIFIED && message.hasCalling =>
      successful(Left(ErrorResponse.Unverified))
    case Some(conv) if conv.verified == Verification.UNVERIFIED =>
      // refusing to send messages to 'degraded' conversation, UI should show error and ask user to verify devices (or ignore it - which will change state to UNKNOWN)
      errors.addConvUnverifiedError(convId, MessageId(message.messageId)) map { _ => Left(ErrorResponse.Unverified) }
    case _ =>
      service.encryptConvMessage(convId, message, retry > 0, previous, recipients) flatMap { content =>
        f(content, retry).future
          .flatMap(r => loopIfMissingClients(r, retry, () => encryptMessageForClients(convId, message, retry + 1, content, recipients)(f)))
      }
  }

  private def loopIfMissingClients(arg: Either[ErrorResponse, MessageResponse], retry: Int, fn: () => Future[Either[ErrorResponse, RemoteInstant]]): Future[Either[ErrorResponse, RemoteInstant]] = arg match {
    case Right(MessageResponse.Success(ClientMismatch(_, _, deleted, time))) =>
      // XXX: we are ignoring redundant clients, we rely on members list to encrypt messages, so if user left the conv then we won't use his clients on next message
      service.deleteClients(deleted) map { _ => Right(time) }
    case Right(MessageResponse.Failure(ClientMismatch(_, missing, deleted, _))) =>
      service.deleteClients(deleted) flatMap { _ =>
        if (retry > 2)
          successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries: $missing")))
        else
          clientsSyncHandler.syncSessions(missing) flatMap {
            case None => fn()
            case Some(err) if retry < 3 =>
              error(s"syncSessions for missing clients failed: $err")
              fn()
            case Some(err) =>
              successful(Left(err))
          }
      }
    case Left(err) =>
      error(s"postOtrMessage failed with error: $err")
      successful(Left(err))
  }

  private def postExternalMessage(clientId: ClientId, convId: ConvId, remoteId: RConvId, message: GenericMessage, recipients: Option[Set[UserId]], nativePush: Boolean): ErrorOrResponse[MessageResponse] = {
    val key = AESKey()
    val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(message))

    CancellableFuture.lift {
      encryptMessageForClients(convId, GenericMessage(Uid(message.messageId), Proto.External(key, sha)), recipients = recipients) { (content, retry) =>
        msgClient.postMessage(remoteId, OtrMessage(clientId, content, Some(data), nativePush), ignoreMissing = retry > 1, recipients)
      } map {
        // that's a bit of a hack, but should be harmless
        case Right(time) => Right(MessageResponse.Success(ClientMismatch(Map.empty, Map.empty, Map.empty, time)))
        case Left(err) => Left(err)
      }
    }
  }

  def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]] = push.afterProcessing {
    def broadcastRecipients = for {
      acceptedOrBlocked <- users.acceptedOrBlockedUsers.head
      myTeam <- teamId.fold(Future.successful(Set.empty[UserData]))(id => usersStorage.getByTeam(Set(id)))
      myTeamIds = myTeam.map(_.id)
    } yield acceptedOrBlocked.keySet ++ myTeamIds

    clients.getSelfClient.flatMap {
      case Some(selfClient) => broadcastRecipients.flatMap { recp =>
        verbose(s"recipients: $recp")
        service.encryptBroadcastMessage(message, useFakeOnError = retry > 0, previous, recp).flatMap { content =>
          otrClient.broadcastMessage(OtrMessage(selfClient.id, content), ignoreMissing = retry > 1, recp).future
            .flatMap(r => loopIfMissingClients(r, retry, () => broadcastMessage(message, retry + 1, content)))
        }
      }
      case None => successful(Left(internalError("Client is not registered")))
    }
  }

  def uploadAssetDataV3(data: LocalData, key: Option[AESKey], mime: Mime = Mime.Default, retention: Retention = Retention.Persistent) = CancellableFuture.lift(clients.getSelfClient).flatMap {
    case Some(_) =>
      key match {
        case Some(k) => CancellableFuture.lift(service.encryptAssetData(k, data)) flatMap {
          case (sha, encrypted, encryptionAlg) => assetClient.uploadAsset(Metadata(retention = retention), encrypted, Mime.Default).map { //encrypted data => Default mime
            case Right(UploadResponse(rId, _, token)) => Right(RemoteData(Some(rId), token, key, Some(sha), Some(encryptionAlg)))
            case Left(err) => Left(err)
          }
        }
        case _ => assetClient.uploadAsset(Metadata(public = true), data, mime).map {
          case Right(UploadResponse(rId, _, _)) => Right(RemoteData(Some(rId)))
          case Left(err) => Left(err)
        }
      }
    case None => CancellableFuture.successful(Left(internalError("Client is not registered")))
  }

  def postSessionReset(convId: ConvId, user: UserId, client: ClientId) = {

    val msg = GenericMessage(Uid(), Proto.ClientAction.SessionReset)

    val convData = convStorage.get(convId) flatMap {
      case None => convStorage.get(ConvId(user.str))
      case conv => successful(conv)
    }

    def msgContent = service.encryptTargetedMessage(user, client, msg) flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        clientsSyncHandler.syncSessions(Map(user -> Seq(client))) flatMap { _ =>
          service.encryptTargetedMessage(user, client, msg)
        }
    }

    convData flatMap {
      case None => successful(SyncResult(internalError(s"conv not found: $convId, for user: $user in postSessionReset")))
      case Some(conv) =>
        clients.getSelfClient flatMap {
          case None => successful(SyncResult(internalError(s"client not registered")))
          case Some(selfClient) =>
            msgContent flatMap {
              case None => successful(SyncResult(internalError(s"session not found for $user, $client")))
              case Some(content) =>
                msgClient.postMessage(conv.remoteId, OtrMessage(selfClient.id, content), ignoreMissing = true).future map {
                  case Right(_) => SyncResult.Success
                  case Left(err) => SyncResult(err)
                }
            }
        }
    }
  }
}

object OtrSyncHandler {
  val MaxInlineSize  = 10 * 1024
  val MaxContentSize = 256 * 1024 // backend accepts 256KB for otr messages, but we would prefer to send less
}
