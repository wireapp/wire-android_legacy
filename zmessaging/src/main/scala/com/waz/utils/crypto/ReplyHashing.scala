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
package com.waz.utils.crypto

import java.nio.ByteBuffer

import com.waz.content.AssetsStorage
import com.waz.model._
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.model.GenericMessage.TextMessage
import com.waz.utils.returning

import scala.concurrent.Future
import java.lang.Long.BYTES
import java.lang.Math.round

import com.waz.api.Message.Type._

trait ReplyHashing {
  def hashMessage(m: MessageData): Future[Sha256]
  def hashMessages(msgs: Seq[MessageData]): Future[Map[MessageId, Sha256]]

  class MissingAssetException(message: String) extends Exception(message)
}

class ReplyHashingImpl(storage: AssetsStorage)(implicit base64: Base64) extends ReplyHashing {

  import com.waz.threading.Threading.Implicits.Background

  override def hashMessage(m: MessageData): Future[Sha256] = hashMessages(Seq(m)).map(_(m.id))

  override def hashMessages(msgs: Seq[MessageData]): Future[Map[MessageId, Sha256]] = {
    val (assetMsgs, otherMsgs) = msgs.partition(m => ReplyHashing.assetTypes.contains(m.msgType))
    for {
      assets     <- storage.getAll(assetMsgs.map(_.assetId))
      assetPairs =  assetMsgs.map(m => m -> assets.find(_.exists(_.id == m.assetId)).flatMap(_.flatMap(_.remoteId)))
      assetShas  <- Future.sequence(assetPairs.map {
                      case (m, Some(rId)) => Future.successful(m.id -> hashAsset(rId, m.time).sha256())
                      case (m, None)      => Future.failed(new MissingAssetException(s"Failed to find asset with id ${m.assetId}"))
                    })
      otherShas  <- Future.sequence(otherMsgs.map {
                      case m if ReplyHashing.textTypes.contains(m.msgType) =>
                        m.protos.last match {
                          case TextMessage(content, _, _, _, _) => Future.successful(m.id -> hashTextReply(content, m.time).sha256())
                          case _                             => Future.successful(m.id -> Sha256.Empty) // should not happen
                        }
                      case m if m.msgType == LOCATION =>
                        Future.successful(m.id -> hashLocation(m.location.get.getLatitude, m.location.get.getLongitude, m.time).sha256())
                      case m =>
                        Future.failed(new IllegalArgumentException(s"Cannot hash illegal reply to message type: ${m.msgType}"))
                    })
    } yield (assetShas ++ otherShas).toMap
  }

  protected[crypto] def hashAsset(assetId: RAssetId, timestamp: RemoteInstant): Sha256Inj = hashTextReply(assetId.str, timestamp)

  protected[crypto] def hashTextReply(content: String, timestamp: RemoteInstant): Sha256Inj = {
    val bytes =
      "\uFEFF".getBytes("UTF-16BE") ++ content.getBytes("UTF-16BE") ++ timestamp.toEpochSec.getBytes
    returning(Sha256Inj.calculate(bytes)) { sha =>
      verbose(s"hashTextReply($content, $timestamp): ${sha.hexString}")
    }
  }

  protected[crypto] def hashLocation(lat: Float, lng: Float, timestamp: RemoteInstant): Sha256Inj = {
    val latNorm: Long = round(lat*1000).toLong
    val lngNorm: Long = round(lng*1000).toLong
    Sha256Inj.calculate(ByteBuffer.allocate(BYTES * 2).putLong(latNorm).putLong(lngNorm).array() ++ timestamp.toEpochSec.getBytes)
  }

  private implicit class RichLong(l: Long) {
    def getBytes: Array[Byte] = ByteBuffer.allocate(BYTES).putLong(l).array()
  }

}

object ReplyHashing {
  private[crypto] val assetTypes = Set(ANY_ASSET, ASSET, VIDEO_ASSET, AUDIO_ASSET)
  private[crypto] val textTypes  = Set(TEXT, RICH_MEDIA, TEXT_EMOJI_ONLY)
}