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

import java.util.UUID

import com.waz.utils.Locales

final case class Handle(private val string: String) extends AnyVal {
  override def toString : String = string

  def startsWithQuery(query: String): Boolean =
    query.nonEmpty && string.startsWith(Handle.stripSymbol(query).toLowerCase)

  def exactMatchQuery(query: String): Boolean =
    string == Handle.stripSymbol(query).toLowerCase

  def withSymbol: String = if (string.startsWith("@")) string else s"@$string"

  def nonEmpty: Boolean = string.nonEmpty
}

object Handle extends (String => Handle){
  val Empty: Handle = Handle("")

  def from(string: String): Handle = {
    val h = string.trim
    if (h.nonEmpty) Handle(h) else Empty
  }

  def random: Handle = Handle(UUID.randomUUID().toString)
  private val handlePattern = """@(.*)""".r
  def transliterated(s: String): String = Locales.transliterate(s)

  def isHandle(input: String): Boolean = input.startsWith("@")

  def stripSymbol(input: String): String = input match {
    case Handle.handlePattern(handle) => handle
    case _ => input
  }
}
