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
package com.waz.service

import java.util.regex.Pattern.{compile, quote}

import com.waz.utils.Locales

final class SearchKey private (val asciiRepresentation: String) extends Serializable {
  private[this] lazy val pattern = compile(s"(.+ )?${quote(asciiRepresentation.toLowerCase)}.*")
  def isAtTheStartOfAnyWordIn(other: SearchKey) = pattern.matcher(other.asciiRepresentation.toLowerCase).matches
  def isEmpty = asciiRepresentation.isEmpty

  override def equals(any: Any): Boolean = any match {
    case other: SearchKey => other.asciiRepresentation.equalsIgnoreCase(asciiRepresentation)
    case _ => false
  }
  override def hashCode: Int = asciiRepresentation.##
  override def toString: String = s"${classOf[SearchKey].getSimpleName}($asciiRepresentation)"
}

object SearchKey extends (String => SearchKey) {
  val Empty = new SearchKey("")
  def apply(name: String): SearchKey = if(name.isEmpty) Empty else new SearchKey(transliterated(tokenize(name)))
  def unsafeRestore(asciiRepresentation: String) = new SearchKey(asciiRepresentation)
  def unapply(k: SearchKey): Option[String] = Some(k.asciiRepresentation)

  def transliterated(s: String): String = Locales.transliterate(s)

  private def tokenize(s: String): String = s.replaceAll("[-|_]+", " ")

  def simple(name: String): SearchKey = if (name.isEmpty) Empty else new SearchKey(tokenize(name))
}
