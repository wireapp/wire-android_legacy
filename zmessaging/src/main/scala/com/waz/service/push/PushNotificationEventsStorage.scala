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
package com.waz.service.push

import android.content.Context
import com.waz.content.Database
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.push.PushNotificationEventsStorage.{EventHandler, EventIndex, PlainWriter}
import com.waz.sync.client.PushNotificationEncoded
import com.waz.utils.TrimmingLruCache.Fixed
import com.wire.signals.EventContext
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}
import org.json.JSONObject

import scala.concurrent.Future

object PushNotificationEventsStorage {
  type PlainWriter = Array[Byte] => Future[Unit]
  type EventIndex = Int

  type EventHandler = () => Future[Unit]
}

trait PushNotificationEventsStorage extends CachedStorage[EventIndex, PushNotificationEvent] {
  def setAsDecrypted(index: EventIndex): Future[Unit]
  def writeClosure(index: EventIndex): PlainWriter
  def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit]
  def saveAll(pushNotifications: Seq[PushNotificationEncoded]): Future[Unit]
  def encryptedEvents: Future[Seq[PushNotificationEvent]]
  def removeRows(rows: Iterable[Int]): Future[Unit]
  def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit]
  def getDecryptedRows(limit: Int = 50): Future[IndexedSeq[PushNotificationEvent]]
}

final class PushNotificationEventsStorageImpl(context: Context, storage: Database, clientId: ClientId)
  extends CachedStorageImpl[EventIndex, PushNotificationEvent](
    new TrimmingLruCache(context, Fixed(1024*1024)), storage)(PushNotificationEventsDao, LogTag("PushNotificationEvents_Cached")
  ) with PushNotificationEventsStorage with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  override def setAsDecrypted(index: EventIndex): Future[Unit] = {
    update(index, u => u.copy(decrypted = true)).map {
      case None =>
        throw new IllegalStateException(s"Failed to set event with index $index as decrypted")
      case _ => ()
    }
  }

  override def writeClosure(index: EventIndex): PlainWriter =
    (plain: Array[Byte]) => update(index, _.copy(decrypted = true, plain = Some(plain))).map(_ => Unit)

  override def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit] =
    update(index, _.copy(decrypted = true, event = MessageEvent.MessageEventEncoder(error), plain = None))
      .map(_ => Unit)

  override def saveAll(pushNotifications: Seq[PushNotificationEncoded]): Future[Unit] = {
    import com.waz.utils._
    def isOtrEventForUs(obj: JSONObject): Boolean = {
      returning(!obj.getString("type").startsWith("conversation.otr") || obj.getJSONObject("data").getString("recipient").equals(clientId.str)) { ret =>
        if (!ret) {
          verbose(l"Skipping otr event not intended for us: $obj")
        }
      }
    }

    val eventsToSave = pushNotifications
      .flatMap { pn =>
        pn.events.toVector.filter(isOtrEventForUs).map { event =>
          (pn.id, event, pn.transient)
        }
      }

    storage.withTransaction { implicit db =>
      val curIndex = PushNotificationEventsDao.maxIndex()
      val nextIndex = if (curIndex == -1) 0 else curIndex+1
      insertAll(eventsToSave.zip(nextIndex until (nextIndex+eventsToSave.length))
        .map { case ((id, event, transient), index) =>
          PushNotificationEvent(id, index, event = event, transient = transient)
        })
    }.future.map(_ => ())
  }

  def encryptedEvents: Future[Seq[PushNotificationEvent]] = values.map(_.filter(!_.decrypted))

  //limit amount of decrypted events we read to avoid overwhelming older phones
  def getDecryptedRows(limit: Int = 50): Future[IndexedSeq[PushNotificationEvent]] = storage.read { implicit db =>
    PushNotificationEventsDao.listDecrypted(limit)
  }

  def removeRows(rows: Iterable[Int]): Future[Unit] = removeAll(rows)

  //This method is called once on app start, so invoke the handler in case there are any events to be processed
  //This is safe as the handler only allows one invocation at a time.
  override def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit] = {
    onAdded.foreach(_ => handler())
    processStoredEvents(handler)
  }

  private def processStoredEvents(processor: () => Future[Unit]): Future[Unit] =
    values.map { nots =>
      if (nots.nonEmpty) {
        processor()
      }
    }
}
