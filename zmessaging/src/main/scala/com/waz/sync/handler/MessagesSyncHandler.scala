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

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.cache.CacheService
import com.waz.content.{GlobalPreferences, MembersStorage, MessagesStorage}
import com.waz.model.AssetData.{ProcessingTaskKey, UploadTaskKey}
import com.waz.model.AssetStatus.{Syncable, UploadCancelled, UploadFailed}
import com.waz.model.GenericContent.{Ephemeral, Knock, Location, MsgEdit}
import com.waz.model._
import com.waz.model.sync.ReceiptType
import com.waz.service.assets._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdater}
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.otr.{OtrClientsService, OtrServiceImpl}
import com.waz.service.tracking.TrackingService
import com.waz.service.{MetaDataService, _}
import com.waz.sync.client.MessagesClient
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.queue.ConvLock
import com.waz.sync.{SyncResult, SyncServiceHandle}
import com.waz.threading.CancellableFuture
import com.waz.threading.CancellableFuture.CancelException
import com.waz.utils.{RichFutureEither, _}
import com.waz.sync.client.{ErrorOr, ErrorOrResponse}
import com.waz.znet2.http.ResponseCode
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.duration.FiniteDuration

class MessagesSyncHandler(selfUserId: UserId,
                          context:    Context,
                          service:    MessagesService,
                          msgContent: MessagesContentUpdater,
                          convEvents: ConversationOrderEventsService,
                          client:     MessagesClient,
                          otr:        OtrServiceImpl,
                          clients:    OtrClientsService,
                          otrSync:    OtrSyncHandler,
                          convs:      ConversationsContentUpdater,
                          storage:    MessagesStorage,
                          assetSync:  AssetSyncHandler,
                          network:    DefaultNetworkModeService,
                          metadata:   MetaDataService,
                          prefs:      GlobalPreferences,
                          sync:       SyncServiceHandle,
                          assets:     AssetService,
                          cache:      CacheService,
                          members:    MembersStorage,
                          tracking:   TrackingService,
                          errors:     ErrorsService, timeouts: Timeouts) {

  import com.waz.threading.Threading.Implicits.Background

  def postDeleted(convId: ConvId, msgId: MessageId): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        val msg = GenericMessage(Uid(), Proto.MsgDeleted(conv.remoteId, msgId))
        otrSync.postOtrMessage(ConvId(selfUserId.str), RConvId(selfUserId.str), msg) map {_.fold(e => SyncResult(e), _ => SyncResult.Success)}
      case None =>
        successful(SyncResult(internalError("conversation not found")))
    }


  def postRecalled(convId: ConvId, msgId: MessageId, recalled: MessageId): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        val msg = GenericMessage(msgId.uid, Proto.MsgRecall(recalled))
        otrSync.postOtrMessage(conv.id, conv.remoteId, msg) flatMap {
          case Left(e) => successful(SyncResult(e))
          case Right(time) =>
            msgContent.updateMessage(msgId)(_.copy(editTime = time, state = Message.Status.SENT)) map { _ => SyncResult.Success }
        }
      case None =>
        successful(SyncResult(internalError("conversation not found")))
    }

  def postReceipt(convId: ConvId, msgId: MessageId, userId: UserId, tpe: ReceiptType): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        val (msg, recipients) = tpe match {
          case ReceiptType.Delivery         => (GenericMessage(msgId.uid, Proto.Receipt(msgId)), Set(userId))
          case ReceiptType.EphemeralExpired => (GenericMessage(msgId.uid, Proto.MsgRecall(msgId)), Set(selfUserId, userId))
        }

        otrSync.postOtrMessage(conv.id, conv.remoteId, msg, Some(recipients), nativePush = false) map {
          case Left(e) => SyncResult(e)
          case Right(_) => SyncResult.Success
        }
      case None =>
        successful(SyncResult(internalError("conversation not found")))
    }

  def postMessage(convId: ConvId, id: MessageId, editTime: RemoteInstant)(implicit convLock: ConvLock): Future[SyncResult] = {

    def shouldGiveUpSending(msg: MessageData) = !network.isOnlineMode || timeouts.messages.sendingTimeout.elapsedSince(msg.time.instant)

    storage.getMessage(id) flatMap { message =>
      message.fold(successful(None: Option[ConversationData]))(msg => convs.convById(msg.convId)) map { conv =>
        (message, conv)
      }
    } flatMap {
      case (Some(msg), Some(conv)) =>
        postMessage(conv, msg, editTime)
          .recover {
            case c: CancelException =>
              SyncResult(ErrorResponse.Cancelled)
            case e: Throwable =>
              error(s"postMessage($conv, $msg) failed", e)
              SyncResult.Failure(Some(internalError(e.getMessage)), shouldRetry = false)
          }
          .flatMap {
            case SyncResult.Success => service.messageSent(conv.id, msg) map (_ => SyncResult.Success)
            case res@SyncResult.Failure(Some(ErrorResponse.Cancelled), _) =>
              verbose(s"postMessage($msg) was cancelled")
              msgContent.updateMessage(id)(_.copy(state = Message.Status.FAILED_READ)) map { _ => res }
            case res@SyncResult.Failure(error, shouldRetry) =>
              val shouldGiveUp = shouldGiveUpSending(msg)
              warn(s"postMessage failed with res: $res, shouldRetry: $shouldRetry, shouldGiveUp: $shouldGiveUp, offline: ${!network.isOnlineMode}, msg.localTime: ${msg.localTime}")
              if (!shouldRetry || shouldGiveUp)
                service.messageDeliveryFailed(conv.id, msg, error.getOrElse(internalError(s"shouldRetry: $shouldRetry, shouldGiveUp: $shouldGiveUp, offline: ${!network.isOnlineMode}"))).map(_ => SyncResult.Failure(error, shouldRetry = false))
              else successful(res)

          }
      case (Some(msg), None) =>
        tracking.exception(new Exception("postMessage failed, couldn't find conversation for msg"), "postMessage failed, couldn't find conversation for msg")
        service.messageDeliveryFailed(msg.convId, msg, internalError("conversation not found")) map (_ => SyncResult.aborted())

      case _ =>
        tracking.exception(new Exception("postMessage failed, couldn't find a message nor conversation"), "postMessage failed, couldn't find a message nor conversation")
        successful(SyncResult.aborted())
    }
  }

  private def postMessage(conv: ConversationData, msg: MessageData, reqEditTime: RemoteInstant)(implicit convLock: ConvLock): Future[SyncResult] = {

    def postTextMessage() = {
      val (gm, isEdit) =
        msg.protos.lastOption match {
          case Some(m@GenericMessage(id, MsgEdit(ref, text))) if !reqEditTime.isEpoch => (m, true) // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ => (GenericMessage.TextMessage(msg), false)
        }

      otrSync.postOtrMessage(conv, gm).flatMap {
        case Right(time) if isEdit =>
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm) map {
            case Some(m) => Right(m)
            case _ => Right(msg.copy(time = RemoteInstant(time.instant)))
          }

        case Right(time) => successful(Right(msg.copy(time = time)))
        case Left(err) => successful(Left(err))
      }
    }

    import Message.Type._

    def post: ErrorOr[RemoteInstant] = msg.msgType match {
      case MessageData.IsAsset() => Cancellable(UploadTaskKey(msg.assetId))(uploadAsset(conv, msg)).future
      case KNOCK => otrSync.postOtrMessage(conv, GenericMessage(msg.id.uid, msg.ephemeral, Proto.Knock()))
      case TEXT | TEXT_EMOJI_ONLY => postTextMessage().map(_.map(_.time))
      case RICH_MEDIA =>
        postTextMessage().flatMap {
          case Right(m) => sync.postOpenGraphData(conv.id, m.id, m.editTime).map(_ => Right(m.time))
          case Left(err) => successful(Left(err))
        }
      case LOCATION =>
        msg.protos.headOption match {
          case Some(GenericMessage(id, loc: Location)) if msg.isEphemeral =>
            otrSync.postOtrMessage(conv, GenericMessage(id, Ephemeral(msg.ephemeral, loc)))
          case Some(proto) =>
            otrSync.postOtrMessage(conv, proto)
          case None =>
            successful(Left(internalError(s"Unexpected location message content: $msg")))
        }
      case tpe =>
        msg.protos.headOption match {
          case Some(proto) if !msg.isEphemeral =>
            verbose(s"sending generic message: $proto")
            otrSync.postOtrMessage(conv, proto)
          case Some(proto) =>
            successful(Left(internalError(s"Can not send generic ephemeral message: $msg")))
          case None =>
            successful(Left(internalError(s"Unsupported message type in postOtrMessage: $tpe")))
        }
    }

    post.flatMap {
      case Right(time) =>
        verbose(s"postOtrMessage($msg) successful $time")
        messageSent(conv.id, msg, time) map { _ => SyncResult.Success }
      case Left(error@ErrorResponse(ResponseCode.Forbidden, _, "unknown-client")) =>
        verbose(s"postOtrMessage($msg), failed: $error")
        clients.onCurrentClientRemoved() map { _ => SyncResult(error) }
      case Left(error@ErrorResponse.Cancelled) =>
        verbose(s"postOtrMessage($msg) cancelled")
        successful(SyncResult(error))
      case Left(error) =>
        verbose(s"postOtrMessage($msg), failed: $error")
        successful(SyncResult(error))
    }.recover {
      case _ : CancelException =>
        verbose(s"postOtrMessage($msg) cancelled")
        SyncResult(ErrorResponse.Cancelled)
    }
  }

  private def uploadAsset(conv: ConversationData, msg: MessageData)(implicit convLock: ConvLock): ErrorOrResponse[RemoteInstant] = {
    verbose(s"uploadAsset($conv, $msg)")

    def postAssetMessage(asset: AssetData, preview: Option[AssetData]): ErrorOrResponse[RemoteInstant] = {
      val proto = GenericMessage(msg.id.uid, msg.ephemeral, Proto.Asset(asset, preview))
      CancellableFuture.lift(otrSync.postOtrMessage(conv, proto) flatMap {
        case Right(time) =>
          verbose(s"posted asset message for: $asset")
          msgContent.updateMessage(msg.id)(_.copy(protos = Seq(proto), time = time)) map { _ => Right(time) }
        case Left(err) =>
          warn(s"posting asset message failed: $err")
          Future successful Left(err)
      })
    }

    //TODO Dean: Update asset status to UploadInProgress after posting original - what about images...?
    def postOriginal(asset: AssetData): ErrorOrResponse[RemoteInstant] =
      if (asset.status != AssetStatus.UploadNotStarted) CancellableFuture successful Right(msg.time)
      else asset.mime match {
        case Mime.Image() => CancellableFuture.successful(Right(msg.time))
        case _ => postAssetMessage(asset, None)
      }

    def sendWithV3(asset: AssetData) = {
      postOriginal(asset).flatMap {
        case Left(err) => CancellableFuture successful Left(err)
        case Right(origTime) =>
          convLock.release()
          //send preview
          CancellableFuture.lift(asset.previewId.map(assets.getAssetData).getOrElse(Future successful None)).flatMap {
            case Some(prev) =>
              service.retentionPolicy(conv).flatMap { retention =>
                assetSync.uploadAssetData(prev.id, retention = retention).flatMap {
                  case Right(Some(updated)) =>
                    postAssetMessage(asset, Some(updated)).map {
                      case (Right(_)) => Right(Some(updated))
                      case (Left(err)) => Left(err)
                    }
                  case Right(None) => CancellableFuture successful Right(None)
                  case Left(err) => CancellableFuture successful Left(err)
                }
              }
            case None => CancellableFuture successful Right(None)
          }.flatMap { //send asset
            case Right(prev) =>
              service.retentionPolicy(conv).flatMap { retention =>
                assetSync.uploadAssetData(asset.id, retention = retention).flatMap {
                  case Right(Some(updated)) => postAssetMessage(updated, prev).map(_.fold(Left(_), _ => Right(origTime)))
                  case Right(None) => CancellableFuture successful Right(RemoteInstant.Epoch) //TODO Dean: what's a good default
                  case Left(err) if err.message.contains(AssetSyncHandler.AssetTooLarge) =>
                    CancellableFuture.lift(errors.addAssetTooLargeError(conv.id, msg.id).map { _ => Left(err) })
                  case Left(err) => CancellableFuture successful Left(err)
                }
              }
            case Left(err) => CancellableFuture successful Left(err)
          }
      }
    }

    //want to wait until asset meta and preview data is loaded before we send any messages
    AssetProcessing.get(ProcessingTaskKey(msg.assetId)).flatMap { _ =>
      CancellableFuture lift assets.getAssetData(msg.assetId).flatMap {
        case None => CancellableFuture successful Left(internalError(s"no asset found for msg: $msg"))
        case Some(asset) if asset.status == AssetStatus.UploadCancelled => CancellableFuture successful Left(ErrorResponse.Cancelled)
        case Some(asset) =>
          verbose(s"Sending asset: $asset")
          sendWithV3(asset)
      }
    }
  }

  private[waz] def messageSent(convId: ConvId, msg: MessageData, time: RemoteInstant) = {
    debug(s"otrMessageSent($convId. $msg, $time)")

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

  def postAssetStatus(cid: ConvId, mid: MessageId, expiration: Option[FiniteDuration], status: Syncable): Future[SyncResult] = {
    def post(conv: ConversationData, asset: AssetData): ErrorOr[Unit] =
      if (asset.status != status) successful(Left(internalError(s"asset $asset should have status $status")))
      else status match {
        case UploadCancelled => otrSync.postOtrMessage(conv, GenericMessage(mid.uid, expiration, Proto.Asset(asset))).flatMapRight(_ => storage.remove(mid))
        case UploadFailed if asset.isImage => successful(Left(internalError(s"upload failed for image $asset")))
        case UploadFailed => otrSync.postOtrMessage(conv, GenericMessage(mid.uid, expiration, Proto.Asset(asset))).mapRight(_ => ())
      }

    for {
      conv   <- convs.storage.get(cid) or internalError(s"conversation $cid not found")
      msg    <- storage.get(mid) or internalError(s"message $mid not found")
      aid    = msg.right.map(_.assetId)
      asset  <- aid.flatMapFuture(id => assets.getAssetData(id).or(internalError(s"asset $id not found")))
      result <- conv.flatMapFuture(c => asset.flatMapFuture(a => post(c, a)))
    } yield result.fold(SyncResult(_), _ => SyncResult.Success)
  }
}
