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

import java.io.File

import android.content.Context
import com.waz.log.LogSE._
import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.cache.CacheStorage.EntryCache
import com.waz.content.Database
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{CacheKey, Uid}
import com.wire.signals.SerialDispatchQueue
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.{Fixed, Relative}
import com.waz.utils.{CachedStorage, CachedStorageImpl, SerialProcessingQueue, TrimmingLruCache}

import scala.concurrent.Future
import scala.concurrent.duration._

trait CacheStorage extends CachedStorage[CacheKey, CacheEntryData]

class CacheStorageImpl(storage: Database, context: Context)
  extends CachedStorageImpl[CacheKey, CacheEntryData](new EntryCache(context), storage)(CacheEntryDao, LogTag("CacheStorage"))
    with CacheStorage
    with DerivedLogTag {

  import com.waz.cache.CacheStorage._

  import com.waz.threading.Threading.Implicits.Background

  onUpdated { _ foreach {
    case (prev, updated) if prev.fileId != updated.fileId => cleanup(prev)
    case _ => // ignore
  } }

  val fileCleanupQueue = new SerialProcessingQueue[(File, Uid)]({ entries =>
    Future {
      verbose(l"deleting cache files: $entries")
      entries foreach { case (path, uid) => entryFile(path, uid).delete() }
    } (Threading.IO)
  }, "CacheFileCleanupQueue")

  override def get(key: CacheKey): Future[Option[CacheEntryData]] = {
    super.get(key) map {
      case Some(entry) if expired(entry) || dataMissing(entry) =>
        super.remove(entry.key)
        cleanup(entry)
        None
      case Some(entry) =>
        updateExpires(entry)
        Some(entry)
      case None =>
        None
    }
  }

  private def updateExpires(entry: CacheEntryData) = {
    val time = System.currentTimeMillis()
    if (entry.lastUsed < time - LastUsedUpdateThrottling) updateInternal(entry.key, _.copy(lastUsed = time))(entry)
  }

  override def remove(key: CacheKey): Future[Unit] = {
    get(key) flatMap {
      case Some(entry) =>
        cleanup(entry)
        super.remove(entry.key)
      case _ => Future.successful(())
    }
  }

  def remove(entry: CacheEntryData) = {
    cleanup(entry)
    super.remove(entry.key)
  }

  def cleanup(entry: CacheEntryData): Unit = entry.path foreach { path => fileCleanupQueue ! (path, entry.fileId) }

  def expired(entry: CacheEntryData) = entry.lastUsed + entry.timeout <= System.currentTimeMillis()

  def dataMissing(entry: CacheEntryData) = entry.data.isEmpty && !entry.path.exists(path => entryFile(path, entry.fileId).exists())
}

object CacheStorage {
  def apply(storage: Database, context: Context): CacheStorage = new CacheStorageImpl(storage, context)

  val LastUsedUpdateThrottling = 1.hour.toMillis

  def entryFile(cacheDir: File, uid: Uid) = new File(cacheDir, uid.str.take(2) + File.separator + uid.str)

  class EntryCache(context: Context) extends TrimmingLruCache[CacheKey, Option[CacheEntryData]](context, Fixed(1024 * 1024) min Relative(.05f)) {
    override def sizeOf(key: CacheKey, value: Option[CacheEntryData]): Int = value.flatMap(_.data).fold(0)(_.length) + key.str.length + value.flatMap(_.path).fold(0)(_.getPath.length) + 56 // data plus some object overhead
  }
}
