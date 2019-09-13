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

import com.waz.model.SearchQuery.{Recommended, RecommendedHandle}

sealed trait SearchQuery {
  def cacheKey: String

  def filter: String = this match {
    case Recommended(str)       => str
    case RecommendedHandle(str) => str
    case _                      => ""
  }
}

object SearchQuery {
  def fromCacheKey(key: String): SearchQuery =
    if (key == TopPeople.cacheKey) TopPeople
    else if (key startsWith Recommended.prefix) Recommended(key substring Recommended.prefix.length)
    else if (key startsWith RecommendedHandle.prefix) RecommendedHandle(key substring RecommendedHandle.prefix.length)
    else throw new IllegalArgumentException(s"not a valid cacheKey: $key")

  case object TopPeople extends SearchQuery {
    val cacheKey = "##top##"
  }

  case class Recommended(searchTerm: String) extends SearchQuery {
    val cacheKey = s"${Recommended.prefix}$searchTerm"
  }
  object Recommended extends (String => Recommended) {
    val prefix = "##recommended##"
  }

  case class RecommendedHandle(searchTerm: String) extends SearchQuery {
    val cacheKey = s"${RecommendedHandle.prefix}$searchTerm"
  }
  object RecommendedHandle extends (String => RecommendedHandle) {
    val prefix = "##recommendedhandle##"
  }
}
