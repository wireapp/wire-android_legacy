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
import com.waz.model.PushNotificationEvents.{DecryptedPushNotificationEventsDao, EncryptedPushNotificationEventsDao}
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.push.PushNotificationEventsStorage.{EventHandler, EventIndex, PlainWriter}
import com.waz.sync.client.PushNotificationEncoded
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.crypto.AESUtils
import com.wire.signals.EventContext
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache, returning}

import scala.concurrent.Future

object PushNotificationEventsStorage {
  type PlainWriter = Array[Byte] => Future[Unit]
  type EventIndex = Int

  type EventHandler = () => Future[Unit]
}

trait PushNotificationEventsStorage {
  def setAsDecrypted(index: EventIndex): Future[Unit]
  def writeClosure(index: EventIndex): PlainWriter
  def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit]
  def saveAllNew(pushNotifications: Seq[PushNotificationEncoded]): Future[Seq[PushNotificationEvent]]
  def encryptedEvents: Future[Seq[PushNotificationEvent]]
  def removeDecryptedEvents(rows: Iterable[Int]): Future[Unit]
  def removeEncryptedEvent(index: EventIndex): Future[Unit]
  def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit]
  def getDecryptedRows: Future[IndexedSeq[PushNotificationEvent]]
  def getAllRows: Future[IndexedSeq[PushNotificationEvent]]
}

final class PushNotificationEventsStorageImpl(context: Context, storage: Database, clientId: ClientId)
  extends PushNotificationEventsStorage with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  val encryptedStorage = new CachedStorageImpl[EventIndex, PushNotificationEvent](
    new TrimmingLruCache(context, Fixed(1024*1024)), storage)(EncryptedPushNotificationEventsDao, LogTag("EncryptedPushNotificationEvents_Cached")
  )

  val decryptedStorage = new CachedStorageImpl[EventIndex, PushNotificationEvent](
    new TrimmingLruCache(context, Fixed(1024*1024)), storage)(DecryptedPushNotificationEventsDao, LogTag("DecryptedPushNotificationEvents_Cached")
  )

  private def insertDecryptedVersion(event: PushNotificationEvent, plain: Option[Array[Byte]]): Future[Unit] = {
    storage.withTransaction { implicit db =>
      val curIndex = DecryptedPushNotificationEventsDao.maxIndex()
      val nextIndex = if (curIndex == -1) 0 else curIndex + 1
      val newEvent = event.copy(index = nextIndex, decrypted = true, plain = plain)
      for {
        _ <- decryptedStorage.insert(newEvent)
        _ <- encryptedStorage.remove(event.index)
      } yield ()
    }
  }.future.flatMap(identity)

  override def setAsDecrypted(index: EventIndex): Future[Unit] = {
    for {
      event <- encryptedStorage.get(index)
      _ <- decryptedStorage.insert(event.get)
    } yield()
  }

  override def writeClosure(index: EventIndex): PlainWriter =
    (plain: Array[Byte]) => {
      verbose(l"Saving event with index ${index} as decrypted with plaintext ${AESUtils.base64(plain)}")
      storage.withTransaction { implicit db =>
        for {
          event <- encryptedStorage.get(index)
          _ <- insertDecryptedVersion(event.get, Some(plain))
          allDecrypted <- this.getDecryptedRows
          _ = verbose(l"After saving index ${index} with plaintext ${AESUtils.base64(plain)}, the DB has the decrypted rows: ${allDecrypted.mkString(", ")}")

        } yield()
      }
    }.future.flatMap(identity)

  override def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit] =

    storage.withTransaction { implicit db =>
      val curIndex = DecryptedPushNotificationEventsDao.maxIndex()
      val nextIndex = if (curIndex == -1) 0 else curIndex + 1
      for {
        event  <- encryptedStorage.get(index)
        _         <- decryptedStorage.insert(event.get.copy(index = nextIndex, event = MessageEvent.errorToEncodedEvent(error), plain = None))
        _         <- encryptedStorage.remove(index)
      } yield ()
    }.future.flatMap(identity)

  override def saveAllNew(pushNotifications: Seq[PushNotificationEncoded]): Future[Seq[PushNotificationEvent]] = {
    val eventsToSave = pushNotifications.flatMap { pn =>
      val (valid, invalid) = pn.events.partition(_.isForUs(clientId))
      invalid.foreach { event => verbose(l"Skipping otr event not intended for us: $event") }
      valid.map { (pn.id, _, pn.transient) }
    }

    storage.withTransaction { implicit db =>
      val curIndex = EncryptedPushNotificationEventsDao.maxIndex()
      val nextIndex = if (curIndex == -1) 0 else curIndex+1
      returning(
        eventsToSave.zip(nextIndex.until(nextIndex+eventsToSave.length)).map {
          case ((id, event, transient), index) => PushNotificationEvent(id, index, event = event, transient = transient)
        }
      ) { eventsToInsert =>
          return encryptedStorage.insertAll(eventsToInsert).map { _.toSeq }
        }
    }
  }

  override def encryptedEvents: Future[IndexedSeq[PushNotificationEvent]] =
    storage.read { implicit db => EncryptedPushNotificationEventsDao.listAll() }

  override def getDecryptedRows: Future[IndexedSeq[PushNotificationEvent]] =
    storage.read { implicit db => DecryptedPushNotificationEventsDao.listAll() }

  override def getAllRows: Future[IndexedSeq[PushNotificationEvent]] =
    storage.read { implicit db =>
        val encrypted = EncryptedPushNotificationEventsDao.listAll()
        val decrypted = DecryptedPushNotificationEventsDao.listAll()
        encrypted ++ decrypted
    }

  def removeDecryptedEvents(rows: Iterable[Int]): Future[Unit] = decryptedStorage.removeAll(rows)

  override def removeEncryptedEvent(index: EventIndex): Future[Unit] = encryptedStorage.remove(index)

  //This method is called once on app start, so invoke the handler in case there are any events to be processed
  //This is safe as the handler only allows one invocation at a time.
  override def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit] = {
    encryptedStorage.onAdded.foreach(_ => handler())
    processStoredEvents(handler)
  }

  private def processStoredEvents(processor: () => Future[Unit]): Future[Unit] = {
    getAllRows.flatMap { notifications =>
      if (notifications.nonEmpty) {
        processor()
      } else {
        Future.successful(())
      }
    }
  }
}
