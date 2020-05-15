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
package com.waz.cache

import java.io._
import java.lang.System._

import android.content.Context
import com.waz.log.LogSE._
import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.content.Database
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.Threading.Implicits.Background
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.crypto.AESUtils
import com.waz.utils.events.Signal
import com.waz.utils.{IoUtils, returning}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import com.waz.service.tracking.TrackingService

trait CacheService {
  def createManagedFile(key: Option[AESKey] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): CacheEntry

  def createForFile(key:              CacheKey        = CacheKey(),
                    mime:             Mime            = Mime.Unknown,
                    name:             Option[String]  = None,
                    cacheLocation:    Option[File]    = None,
                    length:           Option[Long]    = None)
                   (implicit timeout: Expiration      = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def addData(key: CacheKey, data: Array[Byte])(implicit timeout: Expiration = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def addStream(key:              CacheKey,
                in:               => InputStream,
                mime:             Mime              = Mime.Unknown,
                name:             Option[String]    = None,
                cacheLocation:    Option[File]      = None,
                length:           Int               = -1,
                execution:        ExecutionContext  = Background)
               (implicit timeout: Expiration        = CacheService.DefaultExpiryTime): Future[CacheEntry]

  // TODO: This one is used only in tests. Get rid of it.
  def addFile(key:              CacheKey,
              src:              File,
              moveFile:         Boolean         = false,
              mime:             Mime            = Mime.Unknown,
              name:             Option[String]  = None,
              cacheLocation:    Option[File]    = None)
             (implicit timeout: Expiration      = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def entryFile(path: File, fileId: Uid): File

  def intCacheDir: File

  def cacheDir: File

  def remove(key: CacheKey): Future[Unit]

  def remove(entry: CacheEntry): Future[Unit]

  def insert(entry: CacheEntry): Future[CacheEntry]

  def move(key:              CacheKey,
           entry:            LocalData,
           mime:             Mime           = Mime.Unknown,
           name:             Option[String] = None,
           cacheLocation:    Option[File]   = None)
          (implicit timeout: Expiration     = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def getEntry(key: CacheKey): Future[Option[CacheEntry]]

  def getOrElse(key: CacheKey, default: => Future[CacheEntry]) = getEntry(key) flatMap {
    case Some(entry) => Future successful entry
    case _           => default
  }

  def deleteExpired(): CancellableFuture[Unit]
  def optSignal(cacheKey: CacheKey): Signal[Option[CacheEntry]]
}

class CacheServiceImpl(context: Context, storage: Database, cacheStorage: CacheStorage, tracking: TrackingService)
  extends CacheService with DerivedLogTag {

  import CacheService._
  import Threading.Implicits.Background

  // create new cache entry for file, return the entry immediately
  def createManagedFile(key: Option[AESKey] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime) = {
    val location = if (key.isEmpty) Some(intCacheDir) else extCacheDir.orElse(Some(intCacheDir))  // use internal storage for unencrypted files
    val entry = CacheEntryData(CacheKey(), None, timeout = timeout.timeout, path = location, encKey = key)
    cacheStorage.insert(entry)
    entry.path foreach { entryFile(_, entry.fileId).getParentFile.mkdirs() }
    new CacheEntry(entry, this)
  }

  def createForFile(key: CacheKey = CacheKey(), mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None, length: Option[Long] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime) =
    add(CacheEntryData(key, None, timeout = timeout.timeout, mimeType = mime, fileName = name, path = cacheLocation.orElse(Some(intCacheDir)), length = length)) // use internal storage for this files as those won't be encrypted


  def addData(key: CacheKey, data: Array[Byte])(implicit timeout: Expiration = CacheService.DefaultExpiryTime) =
    add(CacheEntryData(key, Some(data), timeout = timeout.timeout))

  def addStream(key: CacheKey, in: => InputStream, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None, length: Int = -1, execution: ExecutionContext = Background)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): Future[CacheEntry] =
    if (length > 0 && length <= CacheService.DataThreshold) {
      Future(IoUtils.toByteArray(in))(execution).flatMap(addData(key, _))
    } else {
      Future(addStreamToStorage(IoUtils.copy(in, _), cacheLocation))(execution) flatMap {
        case Success((fileId, path, encKey, len)) =>
          verbose(l"added stream to storage: $path, with key: $encKey")
          add(CacheEntryData(key, timeout = timeout.timeout, path = Some(path), fileId = fileId, encKey = encKey, fileName = name, mimeType = mime, length = Some(len)))
        case Failure(c: CancelException) =>
          Future.failed(c)
        case Failure(e) =>
          tracking.exception(e, s"addStream failed")
          Future.failed(e)
      }
    }

  def addFile(key: CacheKey, src: File, moveFile: Boolean = false, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): Future[CacheEntry] =
    addStreamToStorage(IoUtils.copy(new FileInputStream(src), _), cacheLocation) match {
      case Success((fileId, path, encKey, len)) =>
        if (moveFile) src.delete()
        add(CacheEntryData(key, timeout = timeout.timeout, path = Some(path), fileId = fileId, encKey = encKey, fileName = name, mimeType = mime, length = Some(len)))
      case Failure(e) =>
        tracking.exception(e, s"addFile failed")
        throw new Exception(s"addFile($key) failed", e)
    }

  private def addStreamToStorage(writer: OutputStream => Long, location: Option[File]): Try[(Uid, File, Option[AESKey], Long)] = {
    def write(dir: File, enc: Option[AESKey]) = {
      val id = Uid()
      def entry(d: File) = returning(entryFile(d, id))(_.getParentFile.mkdirs())

      Try(writer(outputStream(enc, new FileOutputStream(entry(dir))))).recoverWith {
        case c: CancelException => Failure(c)
        case t: Throwable =>
          if (enc.isDefined) Try(writer(outputStream(None, new FileOutputStream(entry(intCacheDir)))))
          else Failure(t)
      } map (len => (id, dir, enc, len))
    }

    location match {
      case Some(dir) => write(dir, None)
      case None =>
        extCacheDir match {
          case Some(dir) => write(dir, Some(AESUtils.randomKey128()))
          case None => write(intCacheDir, None)
        }
    }
  }

  def move(key: CacheKey, entry: LocalData, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime) = {
    verbose(l"move($key)")

    def copy() = addStream(key, entry.inputStream, mime, name, cacheLocation, entry.length)

    val location = cacheLocation.getOrElse(cacheDir)
    (entry match {
      case ce: CacheEntry if ce.data.path.contains(location) =>
        // move file to avoid copying, this should be much faster, and is safe when moving entries in the same cache location
        val prev = ce.data
        val moved = new CacheEntryData(key, prev.data, timeout = timeout.timeout, path = Some(location), encKey = prev.encKey, mimeType = mime, fileName = name)
        val prevFile = entryFile(location, prev.fileId)
        if (!prevFile.exists() || prevFile.renameTo(entryFile(location, moved.fileId))) {
          cacheStorage.insert(moved) map { e => new CacheEntry(e, this) }
        } else {
          copy()
        }
      case _ =>
        copy()
    }) map { current =>
      verbose(l"moved $current, file exists: ${current.cacheFile.exists()}, deleting entry")
      entry.delete()
      current
    }
  }

  private def add(entry: CacheEntryData) =
    cacheStorage.insert(entry) map { e =>
      e.path foreach { entryFile(_, e.fileId).getParentFile.mkdirs() }
      new CacheEntry(e, this)
    }

  def extCacheDir = Option(context.getExternalCacheDir).filter(_.isDirectory)
  def intCacheDir: File = context.getCacheDir
  def cacheDir: File = extCacheDir.getOrElse(intCacheDir)

  def getEntry(key: CacheKey): Future[Option[CacheEntry]] = {
    verbose(l"fileCache getEntry with key $key")
    cacheStorage.get(key).map {
      case Some(e) =>  verbose(l"e: $e"); Some(new CacheEntry(e, this))
      case None => verbose(l"none"); None
    }.recover {
      case e: Throwable => warn(l"failed", e); None
    }
  }

  def remove(key: CacheKey): Future[Unit] = cacheStorage.remove(key)

  def remove(entry: CacheEntry): Future[Unit] = {
    verbose(l"remove($entry)")
    cacheStorage.remove(entry.data.key)
  }

  def deleteExpired(): CancellableFuture[Unit] = {
    val currentTime = currentTimeMillis()
    storage { implicit db =>
      val entries = CacheEntryDao.findAllExpired(currentTime)
      CacheEntryDao.deleteExpired(currentTime)
      entries
    }.map { entries =>
      entries.map(_.key) foreach cacheStorage.remove
      entries
    }.map { _ foreach (entry => entry.path foreach { path => entryFile(path, entry.fileId).delete() }) }
  }

  def entryFile(path: File, fileId: Uid) = CacheStorage.entryFile(path, fileId)

  def insert(entry: CacheEntry) = cacheStorage.insert(entry.data).map(d => new CacheEntry(d, this))

  def optSignal(cacheKey: CacheKey): Signal[Option[CacheEntry]] = cacheStorage.optSignal(cacheKey).map {
    case Some(data) => Some(new CacheEntry(data, this))
    case None => None
  }
}

object CacheService {

  def apply(context: Context, storage: Database, cacheStorage: CacheStorage, tracking: TrackingService) = new CacheServiceImpl(context, storage, cacheStorage, tracking)
  def apply(context: Context, storage: Database, tracking: TrackingService): CacheService = CacheService(context, storage, CacheStorage(storage, context), tracking)

  val DataThreshold = 4 * 1024 // amount of data stored in db instead of a file
  val TemDataExpiryTime = 12.hours
  val DefaultExpiryTime = 7.days

  def outputStream(key: Option[AESKey], os: OutputStream) = key.fold(os) { AESUtils.outputStream(_, os) }

  def inputStream(key: Option[AESKey], is: InputStream) = key.fold(is) { AESUtils.inputStream(_, is) }
}

case class Expiration(timeout: Long)

object Expiration {
  import scala.language.implicitConversions

  implicit def in(d: Duration) : Expiration = if (d.isFinite()) Expiration(d.toMillis) else Expiration(1000L * 3600L * 24L * 365L * 1000L) // 1000 years (don't use Long.MaxValue due to overflow dangers)
}
