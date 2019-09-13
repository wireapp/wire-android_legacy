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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.{SearchQuery, SearchQueryCache}
import com.waz.model.SearchQueryCache.SearchQueryCacheDao
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import org.threeten.bp.Instant

import scala.concurrent.Future

trait SearchQueryCacheStorage extends CachedStorage[SearchQuery, SearchQueryCache] {
  def deleteBefore(i: Instant): Future[Unit]
}

class SearchQueryCacheStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[SearchQuery, SearchQueryCache](new TrimmingLruCache(context, Fixed(20)), storage)(SearchQueryCacheDao, LogTag("SearchQueryCacheStorage"))
  with SearchQueryCacheStorage {
  import Threading.Implicits.Background
  def deleteBefore(i: Instant): Future[Unit] = storage(SearchQueryCacheDao.deleteBefore(i)(_)).future.flatMap(_ => deleteCached(_.timestamp.isBefore(i)))
}