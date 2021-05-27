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
import com.waz.utils.returning

final case class SearchQuery private (query: String, domain: String, handleOnly: Boolean) {
  val isEmpty: Boolean = query.isEmpty && domain.isEmpty

  def hasDomain: Boolean = domain.nonEmpty

  def withDomain(domain: String): SearchQuery = copy(domain = domain)

  lazy val cacheKey: String = {
    val prefix = if (handleOnly) SearchQuery.recommendedHandlePrefix else SearchQuery.recommendedPrefix
    if (domain.isEmpty) s"$prefix$query" else s"$prefix$query@$domain"
  }
}

object SearchQuery {
  val Empty: SearchQuery = SearchQuery("", "", handleOnly = false)

  private val recommendedPrefix = "##recommended##"
  private val recommendedHandlePrefix = "##recommendedhandle##"

  implicit val SearchQueryLogShow: LogShow[SearchQuery] = LogShow.create(sq => s"SearchQuery(${sq.query}, ${sq.handleOnly})")

  def apply(str: String): SearchQuery = {
    val isHandle = Handle.isHandle(str)
    val query = if (isHandle) Handle.stripSymbol(str) else str
    val (queryWithoutDomain, domain) =
      if (query.contains("@")) {
        val split = query.split("@")
        (split(0).trim, split(1).trim) // the domain shouldn't contain the @ sign, so we can assume there are only two elements in the split
      } else
        (query.trim, "")

    returning(SearchQuery(queryWithoutDomain, domain, isHandle)) { sq =>
      verbose(l"SearchQuery.apply, str: $str, search query: $sq")(LogTag("SearchQuery"))
    }
  }

  def fromCacheKey(key: String): SearchQuery =
    if (key.startsWith(recommendedPrefix)) SearchQuery(key.substring(recommendedPrefix.length))
    else if (key.startsWith(recommendedHandlePrefix)) SearchQuery(key.substring(recommendedHandlePrefix.length))
    else throw new IllegalArgumentException(s"not a valid cacheKey: $key")
}
