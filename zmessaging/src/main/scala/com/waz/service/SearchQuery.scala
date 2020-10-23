/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.service

import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogShow
import com.waz.model.Handle
import com.waz.log.LogSE._

case class SearchQuery private (str: String, handleOnly: Boolean) {
  val isEmpty: Boolean = str.isEmpty

  lazy val cacheKey: String = (if (handleOnly) SearchQuery.recommendedHandlePrefix else SearchQuery.recommendedPrefix) + str
}

object SearchQuery {
  val Empty = SearchQuery("", handleOnly = false)

  val recommendedPrefix = "##recommended##"
  val recommendedHandlePrefix = "##recommendedhandle##"

  implicit val SearchQueryLogShow: LogShow[SearchQuery] = LogShow.create(sq => s"SearchQuery(${sq.str}, ${sq.handleOnly})")

  def apply(str: String): SearchQuery =
    if (Handle.isHandle(str)) {
      verbose(l"SearchQuery.apply, str: $str, is handle")(LogTag("SearchQuery"))
      SearchQuery(Handle.stripSymbol(str), handleOnly = true)
    } else {
      verbose(l"SearchQuery.apply, str: $str, not a handle")(LogTag("SearchQuery"))
      SearchQuery(str, handleOnly = false)
    }

  def fromCacheKey(key: String): SearchQuery =
    if (key.startsWith(recommendedPrefix)) SearchQuery(key.substring(recommendedPrefix.length))
    else if (key.startsWith(recommendedHandlePrefix)) SearchQuery(key.substring(recommendedHandlePrefix.length))
    else throw new IllegalArgumentException(s"not a valid cacheKey: $key")
}
