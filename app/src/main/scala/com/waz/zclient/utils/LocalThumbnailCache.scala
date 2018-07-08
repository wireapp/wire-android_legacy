/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.utils

import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.waz.utils.TrimmingLruCache.CacheSize
import com.waz.utils.wrappers.{Bitmap, Context}
import com.waz.utils.{Cache, TrimmingLruCache, returning}
import com.waz.zclient.utils.LocalThumbnailCache._

class LocalThumbnailCache(lru: Cache[Thumbnail, Bitmap]) {

  def getOrCreate(thumbnail: Thumbnail): Bitmap = Option(lru.get(thumbnail)).getOrElse {
    returning(
      Bitmap.fromAndroid(
        ThumbnailUtils.extractThumbnail(
          BitmapFactory.decodeFile(thumbnail.path, opts),
          thumbnail.width,
          thumbnail.height
        )
      )
    )(lru.put(thumbnail, _))
  }
}

object LocalThumbnailCache {
  private val opts = returning(new BitmapFactory.Options) {
    _.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
  }

  case class Thumbnail(path: String, width: Int, height: Int)

  def apply(context: Context) = new LocalThumbnailCache(
    new TrimmingLruCache[Thumbnail, Bitmap](context, CacheSize(total => math.max(1 * 1024 * 1024, (total - 5 * 1024 * 1024) / 2))) {
      override def sizeOf(id: Thumbnail, bitmap: Bitmap): Int = bitmap.getAllocationByteCount
    }
  )
}
