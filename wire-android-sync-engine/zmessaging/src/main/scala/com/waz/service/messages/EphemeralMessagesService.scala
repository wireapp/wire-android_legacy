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
package com.waz.service.messages

import com.waz.log.LogSE._
import com.waz.api.Message
import com.waz.content.{MessagesStorage, ZmsDatabase}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetStatus.{UploadDone, UploadFailed}
import com.waz.model.GenericContent._
import com.waz.model.MessageData.MessageDataDao
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.model.sync.ReceiptType
import com.waz.service.assets.AssetService
import com.waz.service.push.PushService
import com.waz.sync.SyncServiceHandle
import com.wire.signals.{CancellableFuture, Serialized}
import com.waz.utils.crypto.ZSecureRandom
import com.wire.signals.Signal
import com.waz.utils._

import scala.concurrent.Future
import scala.concurrent.duration._

class EphemeralMessagesService(selfUserId: UserId,
                               clientId:   ClientId,
                               messages:   MessagesContentUpdater,
                               storage:    MessagesStorage,
                               db:         ZmsDatabase,
                               sync:       SyncServiceHandle,
                               push:       PushService,
                               assets:     AssetService) extends DerivedLogTag {
  import EphemeralMessagesService._
  import com.waz.threading.Threading.Implicits.Background
  
  private val nextExpiryTime = Signal[LocalInstant](LocalInstant.Max)

  val init = removeExpired()

  nextExpiryTime {
    case LocalInstant.Max => // nothing to expire
    case time => CancellableFuture.delayed((time.toEpochMilli - LocalInstant.Now.toEpochMilli).millis) { removeExpired() }
  }

  storage.onAdded { msgs =>
    updateNextExpiryTime(msgs.flatMap(_.expiryTime))
  }

  storage.onUpdated { updates =>
    updateNextExpiryTime(updates.flatMap(_._2.expiryTime))
  }

  private def updateNextExpiryTime(times: Seq[LocalInstant]) = if (times.nonEmpty) {
    val time = times.min
    nextExpiryTime.mutate(_ min time)
  }

  private def removeExpired() = Serialized.future(s"removeExpired $selfUserId") {
    verbose(l"removeExpired")
    nextExpiryTime ! LocalInstant.Max
    db.read { implicit db =>
      val time = LocalInstant.Now
      MessageDataDao.findExpiring() acquire { msgs =>
        val (expired, rest) = msgs.toStream.span(_.expiryTime.exists(_ <= time))
        rest.headOption.flatMap(_.expiryTime) foreach { time =>
          nextExpiryTime.mutate(_ min time)
        }
        expired.toVector
      }
    } flatMap { expired =>
      val (toObfuscate, toRemove) = expired.partition(_.userId == selfUserId)
      for {
        _ <- messages.deleteOnUserRequest(toRemove.map(_.id))
        // recalling message, this informs the sender that message is already expired
        _ <- Future.traverse(toRemove) { m => sync.postReceipt(m.convId, Seq(m.id), m.userId, ReceiptType.EphemeralExpired) }
        _ <- storage.updateAll2(toObfuscate.map(_.id), obfuscate)
      } yield ()
    }
  }

  private def obfuscate(msg: MessageData): MessageData = {
    import Message.Type._
    verbose(l"obfuscate($msg)")

    def obfuscate(text: String) = text.map { c =>
      if (c.isWhitespace) c else randomChars.next
    }

    msg.msgType match {
      case TEXT | TEXT_EMOJI_ONLY =>
        msg.copy(expired = true, content = Nil, protos = Seq(GenericMessage(msg.id.uid, Text(obfuscate(msg.contentString), Nil, msg.links, msg.protoQuote, expectsReadConfirmation = false))))
      case RICH_MEDIA =>
        val content = msg.content map { ct =>
          ct.copy(content = obfuscate(ct.content), openGraph = None) //TODO: asset and rich media
        }
        msg.copy(expired = true, content = content, protos = Seq(GenericMessage(msg.id.uid, Text(obfuscate(msg.contentString), Nil, msg.links, msg.protoQuote, expectsReadConfirmation = false)))) // TODO: obfuscate links
      case VIDEO_ASSET | AUDIO_ASSET =>
        removeSource(msg)
        msg.copy(expired = true)
      case IMAGE_ASSET | ANY_ASSET => // other assets are removed in removeExpired
        msg.copy(expired = true)
      case LOCATION =>
        val (name, zoom) = msg.location.fold(("", 14)) { l => (obfuscate(l.getName), l.getZoom) }
        msg.copy(expired = true, content = Nil, protos = Seq(GenericMessage(msg.id.uid, Location(0, 0, name, zoom, expectsReadConfirmation = false))))
      case _ =>
        msg.copy(expired = true)
    }
  }

  private def removeSource(msg: MessageData): Unit = assets.getAsset(AssetId(msg.id.str)).collect {
    case asset if selfUserId == msg.userId => assets.delete(asset.id) // only on the sender side - the receiver side is handled in removeExpired
  }

  // start expiration timer for ephemeral message
  def onMessageRead(id: MessageId) = for {
    drift <- push.beDrift.head.map(_.asScala)
    _ <- storage.update(id, { msg =>
      if (shouldStartTimer(msg)) msg.copy(expiryTime = msg.ephemeral.map { exp =>
        msg.userId match {
          case `selfUserId` =>
            val curWithDrift = LocalInstant.Now.toRemote(drift)
            //subtract send time to try and obfuscate on all clients at roughly the same time
            //in case the BE drift is inaccurate and the send time is in the future, clamp the time to now
            (curWithDrift + (exp - msg.time.remainingUntil(curWithDrift))).toLocal(drift) //subtract drift to get back to local time
          case _ => exp.fromNow()
        }
      })
      else msg
    })
  } yield {}

  private def shouldStartTimer(msg: MessageData) = {
    if (msg.ephemeral.isEmpty || msg.expiryTime.isDefined || msg.state != Message.Status.SENT) false
    else if (msg.isAssetMessage) {
      // check if asset was fully uploaded
      msg.protos.exists {
        case GenericMessage(_, Ephemeral(_, Asset(AssetData.WithStatus(UploadDone | UploadFailed), _))) => true
        case GenericMessage(_, Ephemeral(_, ImageAsset(AssetData.WithStatus(UploadDone | UploadFailed)))) => true
        case _ => false
      }
    }
    else true
  }
}

object EphemeralMessagesService {

  val randomChars = {
    val cs = ('a' to 'z') ++ ('A' to 'Z')
    Iterator continually cs(ZSecureRandom.nextInt(cs.size))
  }
}
