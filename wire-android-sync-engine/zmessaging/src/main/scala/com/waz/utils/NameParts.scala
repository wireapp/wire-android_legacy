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
package com.waz.utils

import java.lang.Character.UnicodeBlock
import java.lang.Character.UnicodeBlock._

import com.waz.utils.Locales.currentLocale

import scala.collection.{GenSet, mutable}
import scala.language.postfixOps

case class NameParts(full: String, first: String, firstWithInitial: String, initials: String)

object NameParts {

  val Empty = NameParts("", "", "", "")

  def parseFrom(name: String): NameParts = {
    val trimmed = name.trim

    if (trimmed.isEmpty) Empty
    else if (trimmed.indexOf(' ') == -1) {
      val blocks = unicodeBlocks(trimmed)
      val initials = if (isCJK(blocks) || isMixed(blocks)) initialsAsianOrMixed(trimmed) else maybeInitial(trimmed).getOrElse("").toUpperCase(currentLocale)
      NameParts(full = name, first = trimmed, firstWithInitial = trimmed, initials = initials)
    } else {
      val parts = trimmed.split("\\s+")

      val first = parts(0)
      val last = parts.last
      val blocks = unicodeBlocks(parts.mkString)

      if (isArabic(blocks)) {
        val (first, last) = arabicFirstAndLast(parts)
        val (initials, _) = initialsAndMaybeLastInitial(first, last)
        NameParts(name, first, trimmed, initials)
      }
      else if (isCJK(blocks)) NameParts(name, trimmed, trimmed, initialsAsianOrMixed(trimmed))
      else if (isMixed(blocks)) {
        NameParts(name, first, first, initialsAsianOrMixed(trimmed))
      } else {

        val (initials, maybeLastInitial) = initialsAndMaybeLastInitial(first, Some(last))
        val firstWithInitial = first + maybeLastInitial.map(" " +).getOrElse("")

        NameParts(name, first, firstWithInitial, initials)
      }
    }
  }

  def arabicFirstAndLast(parts: Array[String]): (String, Option[String]) = {
    val habib = "حبيبا"
    val amat = "امه"
    val abd = "عبد"

    if (Set(abd, habib, amat) contains parts(0)) {
      if (parts.size <= 2) (parts mkString " ", None) else (s"${parts(0)} ${parts(1)}", Some(parts.last))
    } else (parts(0), if (parts.size > 1) Some(parts.last) else None)
  }

  def initialsAndMaybeLastInitial(first: String, last: Option[String]): (String, Option[String]) = {
    val Seq(firstInitial, lastInitial) = last match {
      case Some(last) => Seq(first, last) map maybeInitial
      case None => Seq(maybeInitial(first), None)
    }
    val initials = Seq(firstInitial, lastInitial).map(_.getOrElse("").toUpperCase(currentLocale)).mkString
    (initials, lastInitial)
  }

  def maybeInitial(part: String): Option[String] = {
    val first = if (part.isEmpty) None else Some(part.codePointAt(0))
    first.flatMap{ char =>
      if (Character.isLetter(char)) Some(String.valueOf(Character.toChars(char))) else None
    }
  }

  def initialsAsianOrMixed(str: String): String = str.substring(0, math.min(str.length, 2))

  def unicodeBlocks(str: String): GenSet[UnicodeBlock] = {
    var index = 0
    val blocks = mutable.HashSet.empty[UnicodeBlock]

    while (index < str.length) {
      val cp = str.codePointAt(index)
      blocks += UnicodeBlock.of(cp)
      index += Character.charCount(cp)
    }

    blocks
  }

  def isArabic(blocks: GenSet[UnicodeBlock]): Boolean = blocks.contains(ARABIC)

  def isCJK(blocks: GenSet[UnicodeBlock]): Boolean = blocks forall cjkBlocks

  private val cjkBlocks = Set(
    HIRAGANA, KATAKANA, KATAKANA_PHONETIC_EXTENSIONS,
    CJK_COMPATIBILITY, CJK_COMPATIBILITY_FORMS, CJK_COMPATIBILITY_IDEOGRAPHS, CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
    CJK_RADICALS_SUPPLEMENT, ENCLOSED_CJK_LETTERS_AND_MONTHS,
    CJK_UNIFIED_IDEOGRAPHS, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)

  def isMixed(blocks: GenSet[UnicodeBlock]): Boolean = !isArabic(blocks) && !isCJK(blocks) && blocks.size > 1
}
