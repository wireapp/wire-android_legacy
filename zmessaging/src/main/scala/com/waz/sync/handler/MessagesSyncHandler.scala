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

import com.waz.api.Message
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.cache.CacheService
import com.waz.content.{MembersStorage, MessagesStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetData.{ProcessingTaskKey, UploadTaskKey}
import com.waz.model.GenericContent.{ButtonAction, DeliveryReceipt, Ephemeral, EphemeralLocation, Knock, Location, MsgDeleted, MsgEdit, MsgRecall, Asset => GAsset, ReadReceipt => GReadReceipt}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.model.errors._
import com.waz.model.sync.ReceiptType
import com.waz.service.BackendConfig.FederationSupport
import com.waz.service.assets.Asset.{General, Image}
import com.waz.service.assets._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.otr.OtrClientsService
import com.waz.service.tracking.TrackingService
import com.waz.service.{BackendConfig, ErrorsService, Timeouts, UserService}
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.{ErrorOr, ErrorOrResponse}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.otr.OtrSyncHandler.{QTargetRecipients, TargetRecipients}
import com.waz.sync.{SyncResult, SyncServiceHandle}
import com.waz.utils._
import com.waz.zms.BuildConfig
import com.waz.znet2.http.ResponseCode
import com.wire.signals.{CancellableFuture, Signal}

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.duration.{Duration, FiniteDuration}

final class MessagesSyncHandler(selfUserId: UserId,
                                backend:    Signal[BackendConfig],
                                service:    MessagesService,
                                msgContent: MessagesContentUpdater,
                                clients:    OtrClientsService,
                                otrSync:    OtrSyncHandler,
                                convs:      ConversationsContentUpdater,
                                storage:    MessagesStorage,
                                sync:       SyncServiceHandle,
                                assets:     AssetService,
                                assetStorage: AssetStorage,
                                uploadAssetStorage: UploadAssetStorage,
                                cache:      CacheService,
                                members:    MembersStorage,
                                users: =>   UserService,
                                tracking:   TrackingService,
                                errors:     ErrorsService,
                                timeouts: Timeouts) extends DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private def federationSupported: Boolean = backend.currentValue.exists { b => b.federationSupport.isSupported }

  private def postOtrMessage(convId: ConvId, gm: GenericMessage, isHidden: Boolean) =
    if (federationSupported) {
      verbose(l"API federation is supported and we send a qualified otr message")
      otrSync.postQualifiedOtrMessage(convId, gm, isHidden)
    } else {
      verbose(l"API federation is NOT supported and we send a regular otr message")
      otrSync.postOtrMessage(convId, gm, isHidden)
    }

  private def postOtrMessage(convId: ConvId,
                             gm: GenericMessage,
                             isHidden: Boolean,
                             specificUsers: Set[UserId],
                             nativePush: Boolean,
                             enforceIgnoreMissing: Boolean
                            ) =
    if (federationSupported) {
      users.qualifiedIds(specificUsers).flatMap { qIds =>
        otrSync.postQualifiedOtrMessage(convId, gm, isHidden, QTargetRecipients.SpecificUsers(qIds), nativePush, enforceIgnoreMissing)
      }
    } else {
      otrSync.postOtrMessage(convId, gm, isHidden, TargetRecipients.SpecificUsers(specificUsers), nativePush, enforceIgnoreMissing)
    }

  def postDeleted(convId: ConvId, msgId: MessageId): Future[SyncResult] =
    convs.convById(convId).flatMap {
      case Some(conv) =>
        val msg = GenericMessage(Uid(), MsgDeleted(conv.remoteId, msgId))
        postOtrMessage(ConvId(selfUserId.str), msg, isHidden = true).map(SyncResult(_))
      case None =>
        successful(Failure("conversation not found"))
    }

  def postRecalled(convId: ConvId, msgId: MessageId, recalled: MessageId): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        val msg = GenericMessage(msgId.uid, MsgRecall(recalled))
        postOtrMessage(conv.id, msg, isHidden = true).flatMap {
          case Left(e) => successful(SyncResult(e))
          case Right(time) =>
            msgContent
              .updateMessage(msgId)(_.copy(editTime = time, state = Message.Status.SENT))
              .map(_ => SyncResult.Success)
        }
      case None =>
        successful(Failure("conversation not found"))
    }

  def postReceipt(convId: ConvId, msgs: Seq[MessageId], userId: UserId, tpe: ReceiptType): Future[SyncResult] =
    convs.convById(convId).flatMap {
      case Some(conv) =>
        val (msg, recipients) = tpe match {
          case ReceiptType.Delivery         => (GenericMessage(msgs.head.uid, DeliveryReceipt(msgs)), Set(userId))
          case ReceiptType.Read             => (GenericMessage(msgs.head.uid, GReadReceipt(msgs)), Set(userId))
          case ReceiptType.EphemeralExpired => (GenericMessage(msgs.head.uid, MsgRecall(msgs.head)), Set(selfUserId, userId))
        }

        postOtrMessage(
          conv.id,
          msg,
          isHidden = true,
          recipients,
          nativePush = false,
          enforceIgnoreMissing = false
        )
        .map(SyncResult(_))
      case None =>
        successful(Failure("conversation not found"))
    }

  def postButtonAction(messageId: MessageId, buttonId: ButtonId, senderId: UserId): Future[SyncResult] =
    storage.get(messageId).flatMap {
      case None      => successful(Failure("message not found"))
      case Some(msg) => for {
        result <- postOtrMessage(
                    msg.convId,
                    GenericMessage(Uid(), ButtonAction(buttonId.str, messageId.str)),
                    isHidden = true,
                    Set(senderId),
                    nativePush = false,
                    enforceIgnoreMissing = true
                  )
        _      <- result.fold(_ => service.setButtonError(messageId, buttonId), _ => Future.successful(()))
      } yield SyncResult(result)
    }

  def postMessage(convId: ConvId, id: MessageId, editTime: RemoteInstant)(implicit info: RequestInfo): Future[SyncResult] =
    storage.getMessage(id).flatMap { message =>
      message
        .fold(successful(None: Option[ConversationData]))(msg => convs.convById(msg.convId))
        .map(conv => (message, conv))
    }.flatMap {
      case (Some(msg), Some(conv)) =>
        postMessage(conv, msg, editTime).flatMap {
          case Right(timeAndId) =>
            verbose(l"postOtrMessage($msg) successful $timeAndId")
            for {
              _ <- service.messageSent(convId, timeAndId._2, timeAndId._1)
              (prevLastTime, lastTime) <- msgContent.updateLocalMessageTimes(convId, msg.time, timeAndId._1)
                                                    .map(_.lastOption.map { case (p, c) => (p.time, c.time)}
                                                    .getOrElse((msg.time, timeAndId._1)))
              // update conv lastRead time if there is no unread message after the message that was just sent
              _ <- convs.storage.update(convId, c => if (!c.lastRead.isAfter(prevLastTime)) c.copy(lastRead = lastTime) else c)
              _ <- convs.updateLastEvent(convId, timeAndId._1)
            } yield SyncResult.Success

          case Left(error) =>
            verbose(l"postOtrMessage($msg) not successful $error")
            for {
              _ <- error match {
                case ErrorResponse(ResponseCode.Forbidden, _, "unknown-client") => clients.onCurrentClientRemoved()
                case _ => Future.successful({})
              }
              syncResult = error match {
                case ErrorResponse(ErrorResponse.ConnectionErrorCode, _, _) => Failure.apply(error)
                case _ => SyncResult(error)
              }
              result <- syncResult match {
                case r: SyncResult.Failure =>
                  verbose(l"postOtrMessage($msg) mark message delivery failed $r")
                  service
                    .messageDeliveryFailed(convId, msg, error)
                    .map(_ => r)
                case r =>
                  verbose(l"postOtrMessage($msg) do not mark message delivery failed $r")
                  Future.successful(r)
              }
            } yield result
        }

      case (Some(msg), None) =>
        service
          .messageDeliveryFailed(msg.convId, msg, internalError("conversation not found"))
          .map(_ => Failure("postMessage failed, couldn't find conversation for msg"))

      case _ =>
        successful(Failure("postMessage failed, couldn't find either message or conversation"))
    }

  /**
    * Sends a message to the given conversation. If the message is an edit, it will also update the message
    * in the database to the new message ID.
    * @return either error, or the remote timestamp and the new message ID
    */
  private def postMessage(conv: ConversationData,
                          msg: MessageData,
                          reqEditTime: RemoteInstant): ErrorOr[(RemoteInstant, MessageId)] = {

    def postTextMessage(): ErrorOr[MessageData] = {

      val adjustedMsg = msg.adjustMentions(true).getOrElse(msg)

      val (gm, isEdit) = adjustedMsg.genericMsgs.lastOption.flatMap { m =>
        m.unpackContent match {
          case _: MsgEdit if !reqEditTime.isEpoch => Some((m, true))
          // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ => None
        }
      }.getOrElse((TextMessage(adjustedMsg), false))

      postOtrMessage(conv.id, gm, isHidden = false).flatMap {
        case Right(time) if isEdit =>
          verbose(l"postOtrMessage successful for edit")
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm).map {
            case Some(m) => Right(m)
            case _       => Right(msg.copy(time = RemoteInstant(time.instant)))
          }
        case Right(time) =>
          verbose(l"postOtrMessage successful for text")
          successful(Right(msg.copy(time = time)))
        case Left(err) =>
          verbose(l"postOtrMessage error: $err")
          successful(Left(err))
      }
    }

    import Message.Type._

    msg.msgType match {
      case _ if msg.isAssetMessage =>
        Cancellable(UploadTaskKey(msg.assetId.get))(uploadAsset(conv, msg)).future.map(_.map((_, msg.id)))
      case KNOCK =>
        postOtrMessage(conv.id, GenericMessage(msg.id.uid, msg.ephemeral, Knock(msg.expectsRead.getOrElse(false), conv.messageLegalHoldStatus)), isHidden = false).map(_.map((_, msg.id)))
      case TEXT | TEXT_EMOJI_ONLY =>
        postTextMessage().map(_.map(data => (data.time, data.id)))
      case RICH_MEDIA =>
        postTextMessage().flatMap {
          case Right(m)  => sync.postOpenGraphData(conv.id, m.id, m.editTime).map(_ => Right(m.time, m.id))
          case Left(err) => successful(Left(err))
        }
      case LOCATION =>
        msg.genericMsgs.headOption.map { m =>
          m.unpack match {
            case (id, loc: Location) if msg.isEphemeral =>
              val expiry = msg.ephemeral.getOrElse(Duration.Zero)
              postOtrMessage(conv.id, GenericMessage(id, Ephemeral(msg.ephemeral, EphemeralLocation(loc.proto, expiry))), isHidden = false).map(_.map((_, msg.id)))
            case _ =>
              postOtrMessage(conv.id, m, isHidden = false).map(_.map((_, msg.id)))
          }
        }.getOrElse {
          successful(Left(internalError(s"Unexpected location message content: $msg")))
        }
      case tpe =>
        verbose(l"post generic message")
        msg.genericMsgs.headOption match {
          case Some(proto) if !msg.isEphemeral =>
            postOtrMessage(conv.id, proto, isHidden = false).map(_.map((_, msg.id)))
          case Some(_) =>
            successful(Left(internalError(s"Can not send generic ephemeral message: $msg")))
          case None =>
            successful(Left(internalError(s"Unsupported message type in postOtrMessage: $tpe")))
        }
    }
  }

  private def uploadAsset(conv: ConversationData, msg: MessageData): ErrorOrResponse[RemoteInstant] = {
    import com.waz.model.errors._

    verbose(l"uploadAsset($conv, $msg)")

    def postAssetMessage(message: GenericMessage, id: GeneralAssetId): CancellableFuture[RemoteInstant] = {
      for {
        time <- postOtrMessage(conv.id, message, isHidden = false).flatMap { case Left(errorResponse) => Future.failed(errorResponse)
          case Right(time) => Future.successful(time)
        }.lift
        _ <- msgContent.updateMessage(msg.id)(_.copy(genericMsgs = Seq(message), time = time, assetId = Some(id))).lift
      } yield time
    }

    //TODO Dean: Update asset status to UploadInProgress after posting original - what about images...?
    def postOriginal(rawAsset: UploadAsset): CancellableFuture[RemoteInstant] =
      if (rawAsset.status != UploadAssetStatus.NotStarted) CancellableFuture.successful(msg.time)
      else rawAsset.details match {
        case _: Image =>
          CancellableFuture.successful(msg.time)
        case _ =>
          postAssetMessage(
            GenericMessage(
              msg.id.uid,
              msg.ephemeral,
              GAsset(rawAsset, None, expectsReadConfirmation = msg.expectsRead.contains(true), conv.messageLegalHoldStatus)
            ),
            rawAsset.id
          )
      }

    def sendWithV3(uploadAssetOriginal: UploadAsset): CancellableFuture[RemoteInstant] = {
      for {
        rawAssetWithMetadata <- uploadAssetOriginal.details match {
          case details: General => CancellableFuture.successful(uploadAssetOriginal.copy(details = details))
          case details => CancellableFuture.failed(FailedExpectationsError(s"We expect that metadata already extracted. Got $details"))
        }
        time <- postOriginal(rawAssetWithMetadata)
        uploadAssetOriginal <- uploadAssetOriginal.preview match {
          case PreviewNotReady => assets.createAndSavePreview(rawAssetWithMetadata).lift
          case _ => CancellableFuture.successful(rawAssetWithMetadata)
        }
        previewAsset <- uploadAssetOriginal.preview match {
          case PreviewNotUploaded(uploadAssetPreviewId) =>
            for {
              previewAsset <- assets.uploadAsset(uploadAssetPreviewId)
              proto = GenericMessage(
                msg.id.uid,
                msg.ephemeral,
                GAsset(uploadAssetOriginal, Some(previewAsset), expectsReadConfirmation = false, conv.messageLegalHoldStatus)
              )
              _ <- postAssetMessage(proto, uploadAssetOriginal.id)
            } yield Some(previewAsset)
          case PreviewUploaded(assetId) =>
            assetStorage.get(assetId).map(Some.apply).lift
          case PreviewEmpty =>
            CancellableFuture.successful(None)
          case PreviewNotReady =>
            CancellableFuture.failed(FailedExpectationsError("We should never get not ready preview in this place"))
        }
        _ <- (previewAsset match {
          case Some(p) => uploadAssetStorage.update(uploadAssetOriginal.id, _.copy(preview = PreviewUploaded(p.id)))
          case None => Future.successful(())
        }).lift
        asset <- assets.uploadAsset(uploadAssetOriginal.id)
        proto = GenericMessage(
          msg.id.uid,
          msg.ephemeral,
          GAsset(asset, previewAsset, expectsReadConfirmation = msg.expectsRead.contains(true), conv.messageLegalHoldStatus)
        )
        _ <- postAssetMessage(proto, asset.id)
      } yield time
    }

    //want to wait until asset meta and preview data is loaded before we send any messages
    for {
      _ <- AssetProcessing.get(ProcessingTaskKey(msg.assetId.get)).lift
      rawAsset <- uploadAssetStorage.find(msg.assetId.collect { case id: UploadAssetId => id }.get).lift
      result <- rawAsset match {
        case None =>
          CancellableFuture.successful(Left(internalError(s"no asset found for msg: $msg")))
        case Some(asset) if asset.status == UploadAssetStatus.Cancelled =>
          CancellableFuture.successful(Left(ErrorResponse.Cancelled))
        case Some(asset) =>
          verbose(l"Sending asset: $asset")
          sendWithV3(asset)
            .map(Right.apply)
            .recover { case response: ErrorResponse => Left(response) }
      }
    } yield result
  }

  private[waz] def messageSent(convId: ConvId, msg: MessageData, time: RemoteInstant) = {

    def updateLocalTimes(conv: ConvId, prevTime: RemoteInstant, time: RemoteInstant) =
      msgContent.updateLocalMessageTimes(conv, prevTime, time) flatMap { updated =>
        val prevLastTime = updated.lastOption.fold(prevTime)(_._1.time)
        val lastTime = updated.lastOption.fold(time)(_._2.time)
        // update conv lastRead time if there is no unread message after the message that was just sent
        convs.storage.update(conv,
          c => if (!c.lastRead.isAfter(prevLastTime)) c.copy(lastRead = lastTime) else c
        )
      }

    for {
      _ <- updateLocalTimes(convId, msg.time, time)
      _ <- convs.updateLastEvent(convId, time)
    } yield ()
  }

  def postAssetStatus(cid: ConvId, mid: MessageId, expiration: Option[FiniteDuration], statusToPost: UploadAssetStatus): Future[SyncResult] = {
    val result = for {
      //TODO Use new storage to avoid explicit 'Option.get'
      (conv, msg) <- convs.storage.get(cid).map(_.get) zip storage.get(mid).map(_.get)
      uploadAsset <- msg.assetId match {
        case Some(id: UploadAssetId) => uploadAssetStorage.get(id)
        case _ => Future.failed(FailedExpectationsError(s"We expect not uploaded asset id. Got ${msg.assetId}."))
      }
      uploadAssetPreview <- uploadAsset.preview match {
        case PreviewUploaded(id) => assetStorage.find(id)
        case _ => Future.successful(None)
      }
      genericAsset <- uploadAsset.status match {
        case assetStatus if assetStatus == statusToPost =>
          Future.successful(GAsset(uploadAsset.asInstanceOf[UploadAsset], uploadAssetPreview, expectsReadConfirmation = false, conv.messageLegalHoldStatus))
        case assetStatus =>
          Future.failed(FailedExpectationsError(s"We expect uploaded asset status $statusToPost. Got $assetStatus."))
      }
      message = GenericMessage(mid.uid, expiration, genericAsset)
      _ <- postOtrMessage(conv.id, message, isHidden = false)
      _ <- statusToPost match {
        case UploadAssetStatus.Cancelled => storage.remove(msg.id)
        case _ => Future.successful(())
      }
    } yield ()

    result.modelToEither.map(_.fold(SyncResult.apply, _ => SyncResult.Success))
  }
}
