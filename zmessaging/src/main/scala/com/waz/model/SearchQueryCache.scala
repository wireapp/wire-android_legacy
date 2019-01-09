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
package com.waz.model

import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import org.json.JSONArray
import org.threeten.bp.Instant

case class SearchQueryCache(query: SearchQuery, timestamp: Instant, entries: Option[Vector[UserId]])
extends Identifiable[SearchQuery] {
  override val id: SearchQuery = query
}

object SearchQueryCache {
  implicit object SearchQueryCacheDao extends Dao[SearchQueryCache, SearchQuery] {
    val Query = text[SearchQuery]('query, _.cacheKey, SearchQuery.fromCacheKey, "PRIMARY KEY")(_.query)
    val Timestamp = timestamp('timestamp)(_.timestamp)
    val Entries = opt(text[Vector[UserId]]('entries, enc, dec))(_.entries)

    private def enc(ids: Vector[UserId]): String = JsonEncoder.array(ids)((arr, id) => arr.put(id.str)).toString
    private def dec(v: String): Vector[UserId] = JsonDecoder.array(new JSONArray(v), (arr, i) => UserId(arr.getString(i)))

    override val idCol = Query

    override val table = Table("SearchQueries", Query, Timestamp, Entries)

    override def apply(implicit cursor: DBCursor): SearchQueryCache = SearchQueryCache(Query, Timestamp, Entries)

    def deleteBefore(i: Instant)(implicit db: DB) = db.delete(table.name, s"${Timestamp.name} < ?", Array(Timestamp(i)))
  }
}
