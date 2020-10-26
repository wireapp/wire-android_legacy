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

import com.waz.model._
import com.waz.log.LogSE._
import com.waz.model.GenericMessage.TextMessage
import com.waz.utils.returning

import scala.concurrent.Future
import java.lang.Long.BYTES
import java.lang.Math.round

import com.waz.api.Message.Type._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.assets.AssetStorage

trait ReplyHashing {
  def hashMessage(m: MessageData): Future[Sha256]
  def hashMessages(msgs: Seq[MessageData]): Future[Map[MessageId, Sha256]]

  class MissingAssetException(message: String) extends Exception(message)
}

class ReplyHashingImpl(storage: AssetStorage) extends ReplyHashing with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def hashMessage(m: MessageData): Future[Sha256] = hashMessages(Seq(m)).map(_(m.id))

  override def hashMessages(msgs: Seq[MessageData]): Future[Map[MessageId, Sha256]] = {
    val (assetMsgs, otherMsgs) = msgs.partition(m => ReplyHashing.assetTypes.contains(m.msgType))
    for {
      assets     <- storage.loadAll(assetMsgs.map(_.assetId).collect { case Some(x: AssetId) => x }.toSet)
      assetPairs =  assetMsgs.map(m => m -> assets.find(a => m.assetId.contains(a.id)).map(_.id))
      assetShas  <- Future.sequence(assetPairs.map {
                      case (m, Some(rId)) => Future.successful(m.id -> hashAsset(rId, m.time))
                      case (m, None)      => Future.failed(new MissingAssetException(s"Failed to find asset with id ${m.assetId}"))
                    })
      otherShas  <- Future.sequence(otherMsgs.map {
                      case m if ReplyHashing.textTypes.contains(m.msgType) =>
                        m.protos.last match {
                          case TextMessage(content, _, _, _, _) => Future.successful(m.id -> hashTextReply(content, m.time))
                          case _                             => Future.successful(m.id -> Sha256.Empty) // should not happen
                        }
                      case m if m.msgType == LOCATION =>
                        Future.successful(m.id -> hashLocation(m.location.get.getLatitude, m.location.get.getLongitude, m.time))
                      case m =>
                        Future.failed(new IllegalArgumentException(s"Cannot hash illegal reply to message type: ${m.msgType}"))
                    })
    } yield (assetShas ++ otherShas).toMap
  }

  protected[crypto] def hashAsset(assetId: AssetId, timestamp: RemoteInstant): Sha256 = hashTextReply(assetId.str, timestamp)

  protected[crypto] def hashTextReply(content: String, timestamp: RemoteInstant): Sha256 = {
    val bytes =
      "\uFEFF".getBytes("UTF-16BE") ++ content.getBytes("UTF-16BE") ++ timestamp.toEpochSec.getBytes
    returning(Sha256.calculate(bytes)) { sha =>
      verbose(l"hashTextReply(${redactedString(content)}, $timestamp): $sha")
    }
  }

  protected[crypto] def hashLocation(lat: Float, lng: Float, timestamp: RemoteInstant): Sha256 = {
    val latNorm: Long = round(lat*1000).toLong
    val lngNorm: Long = round(lng*1000).toLong
    Sha256.calculate(ByteBuffer.allocate(BYTES * 2).putLong(latNorm).putLong(lngNorm).array() ++ timestamp.toEpochSec.getBytes)
  }

  private implicit class RichLong(l: Long) {
    def getBytes: Array[Byte] = ByteBuffer.allocate(BYTES).putLong(l).array()
  }

}

object ReplyHashing {
  private[crypto] val assetTypes = Set(ANY_ASSET, IMAGE_ASSET, VIDEO_ASSET, AUDIO_ASSET)
  private[crypto] val textTypes  = Set(TEXT, RICH_MEDIA, TEXT_EMOJI_ONLY)
}
