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
package com.waz.ui

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.model.AssetId
import com.waz.utils.TrimmingLruCache.CacheSize
import com.waz.utils.wrappers.Context
import com.waz.utils.{Cache, TrimmingLruCache}

trait MemoryImageCache {
  def reserve(id: AssetId, width: Int, height: Int): Unit
}

class MemoryImageCacheImpl(context: Context) extends MemoryImageCache with DerivedLogTag {
  import MemoryImageCache._

  private lazy val lru: Cache[AssetId, MemoryImageCache.Entry] =
    new TrimmingLruCache[AssetId, Entry](context, CacheSize(total => math.max(5 * 1024 * 1024, (total - 30 * 1024 * 1024) / 2))) {
      override def sizeOf(id: AssetId, value: Entry): Int = value.size
    }

  override def reserve(id: AssetId,  width: Int, height: Int): Unit = lru.synchronized {
    Option(lru.get(id)).getOrElse(lru.put(id, EmptyEntry(width * height * 4 + 256)))
  }
}

object MemoryImageCache {
  sealed trait Entry {
    def size: Int
  }

  // used to reserve space
  case class EmptyEntry(size: Int) extends Entry {
    require(size > 0)
  }

  sealed trait BitmapRequest extends SafeToLog {
    val width: Int
    val mirror: Boolean = false
  }
}
