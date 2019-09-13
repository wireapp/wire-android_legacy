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
import com.waz.log.LogSE._
import com.waz.model.AssetId
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.ui.MemoryImageCache.BitmapRequest.{Blurred, Regular, Round, Single}
import com.waz.utils.{Cache, TrimmingLruCache}
import com.waz.utils.TrimmingLruCache.CacheSize
import com.waz.utils.wrappers.{Bitmap, Context, EmptyBitmap}

trait MemoryImageCache {
  def get(id: AssetId, req: BitmapRequest, imgWidth: Int): Option[Bitmap]
  def add(id: AssetId, req: BitmapRequest, bmp: Bitmap): Unit
  def remove(id: AssetId, req: BitmapRequest): Unit
  def reserve(id: AssetId,  req: BitmapRequest, width: Int, height: Int): Unit
  def reserve(id: AssetId, req: BitmapRequest, size: Int): Unit
  def clear(): Unit
  def apply(id: AssetId, req: BitmapRequest, imgWidth: Int, load: => CancellableFuture[Bitmap]): CancellableFuture[Bitmap]
}

class MemoryImageCacheImpl(lru: Cache[MemoryImageCache.Key, MemoryImageCache.Entry])
  extends MemoryImageCache
    with DerivedLogTag {

  import MemoryImageCache._

  override def get(id: AssetId, req: BitmapRequest, imgWidth: Int): Option[Bitmap] = Option(lru.get(Key(id, tag(req)))) flatMap {
    case BitmapEntry(bmp) if bmp.getWidth >= req.width || (imgWidth > 0 && bmp.getWidth > imgWidth) => Some(bmp)
    case _ => None
  }

  override def add(id: AssetId, req: BitmapRequest, bmp: Bitmap): Unit = if (bmp != null && !bmp.isEmpty) {
    lru.put(Key(id, tag(req)), BitmapEntry(bmp))
  }

  override def remove(id: AssetId, req: BitmapRequest): Unit = lru.remove(Key(id, tag(req)))

  override def reserve(id: AssetId,  req: BitmapRequest, width: Int, height: Int): Unit = reserve(id, req, width * height * 4 + 256)

  override def reserve(id: AssetId, req: BitmapRequest, size: Int): Unit = lru.synchronized {
    val key = Key(id, tag(req))
    Option(lru.get(key)) getOrElse lru.put(key, EmptyEntry(size))
  }

  override def clear(): Unit = lru.evictAll()

  override def apply(id: AssetId, req: BitmapRequest, imgWidth: Int, load: => CancellableFuture[Bitmap]): CancellableFuture[Bitmap] =
    get(id, req, imgWidth) match {
      case Some(bitmap) =>
        verbose(l"found bitmap for req: $req")
        CancellableFuture.successful(bitmap)
      case None =>
        verbose(l"no bitmap for req: $req, loading...")
        val future = load
        future.onSuccess {
          case EmptyBitmap => // ignore
          case img => add(id, req, img)
        }(Threading.Ui)
        future
    }
}

object MemoryImageCache {

  case class Key(id: AssetId, string: String)

  sealed trait Entry {
    def size: Int
  }

  case class BitmapEntry(bmp: Bitmap) extends Entry {
    override def size = bmp.getByteCount
  }

  // used to reserve space
  case class EmptyEntry(size: Int) extends Entry {
    require(size > 0)
  }

  def apply(context: Context) = new MemoryImageCacheImpl(newTrimmingLru(context))

  def newTrimmingLru(context: Context):Cache[Key, Entry] =
    new TrimmingLruCache[Key, Entry](context, CacheSize(total => math.max(5 * 1024 * 1024, (total - 30 * 1024 * 1024) / 2))) {
      override def sizeOf(id: Key, value: Entry): Int = value.size
    }

  sealed trait BitmapRequest extends SafeToLog {
    val width: Int
    val mirror: Boolean = false
  }

  object BitmapRequest {
    case class Regular(width: Int = 0, override val mirror: Boolean = false) extends BitmapRequest
    case class Single(width: Int = 0, override val mirror: Boolean = false) extends BitmapRequest
    case class Round(width: Int = 0, borderWidth: Int = 0, borderColor: Int = 0) extends BitmapRequest
    case class Blurred(width: Int = 0, blurRadius: Int = 1, blurPasses: Int = 1) extends BitmapRequest
  }

  //The width makes BitmapRequests themselves bad keys, remove them
  def tag(request: BitmapRequest) = request match {
    case Regular(_, mirror) => s"Regular-$mirror"
    case Single(_, mirror) => s"Single-$mirror"
    case Round(_, bw, bc) => s"Round-$bw-$bc"
    case Blurred(_, br, bp) => s"Blurred-$br-$bp"
  }
}
