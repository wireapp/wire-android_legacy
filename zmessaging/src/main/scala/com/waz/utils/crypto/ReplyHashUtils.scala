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

trait ReplyHashing {
  def hashMessage(m: MessageData): Future[Sha256]

  class MissingAssetException(message: String) extends Exception(message)
}

class ReplyHashingImpl(storage: AssetsStorage)(implicit base64: Base64) extends ReplyHashing {

  import com.waz.threading.Threading.Implicits.Background

  override def hashMessage(m: MessageData): Future[Sha256] = {
    import com.waz.api.Message.Type._
    m.msgType match {
      case TEXT | RICH_MEDIA | TEXT_EMOJI_ONLY =>
        m.protos.last match {
          case TextMessage(content, _, _, _) =>
            Future.successful(hashTextReply(content, m.time).sha256())
          case _ =>
            Future.successful(Sha256.Empty) // should not happen
        }
      case LOCATION =>
        Future.successful(hashLocation(m.location.get.getLatitude, m.location.get.getLongitude, m.time).sha256())
      case ANY_ASSET | ASSET | VIDEO_ASSET | AUDIO_ASSET =>
        storage.get(m.assetId).map(_.flatMap(_.remoteId)).flatMap {
          case Some(rId) => Future.successful(hashAsset(rId, m.time).sha256())
          case None => Future.failed(new MissingAssetException(s"Failed to find asset with id ${m.assetId}"))
        }
      case _ =>
        Future.failed(new IllegalArgumentException(s"Cannot hash illegal reply to message type: ${m.msgType}"))
    }
  }

  protected[crypto] def hashAsset(assetId: RAssetId, timestamp: RemoteInstant): Sha256Inj = hashTextReply(assetId.str, timestamp)

  protected[crypto] def hashTextReply(content: String, timestamp: RemoteInstant): Sha256Inj = {
    val bytes =
      "\uFEFF".getBytes("UTF-16BE") ++ content.getBytes("UTF-16BE") ++ timestamp.toEpochMilli.getBytes
    returning(Sha256Inj.calculate(bytes)) { sha =>
      verbose(s"hashTextReply($content, ${timestamp.javaDate}): ${sha.hexString}")
    }
  }

  protected[crypto] def hashLocation(lat: Float, lng: Float, timestamp: RemoteInstant): Sha256Inj = {
    val latNorm: Long = round(lat*1000).toLong
    val lngNorm: Long = round(lng*1000).toLong
    Sha256Inj.calculate(ByteBuffer.allocate(BYTES * 2).putLong(latNorm).putLong(lngNorm).array() ++ timestamp.toEpochMilli.getBytes)
  }

  private implicit class RichLong(l: Long) {
    def getBytes: Array[Byte] =
      ByteBuffer.allocate(BYTES).putLong(l).array()
  }

}
