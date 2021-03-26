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

import java.lang.Character.UnicodeBlock._
import java.text.{CollationKey, Collator}
import java.util
import java.util.{Comparator, Locale}

import android.annotation.TargetApi
import android.content.res.Configuration
import android.os.Build
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.ZMessaging
import com.waz.utils

import scala.annotation.tailrec
import scala.util.Try

object Locales extends DerivedLogTag {
  def currentLocale =
    (for {
      context   <- Option(ZMessaging.context)
      resources <- Option(context.getResources)
      config    <- Option(resources.getConfiguration)
      locale    <- localeOptFromConfig(config)
    } yield locale) getOrElse Locale.getDefault

  lazy val bcp47 = AndroidLanguageTags.create

  def localeOptFromConfig(config: Configuration): Option[Locale] = Option(config.locale)

  // the underlying native collator shows no signs of being thread-safe & locale might change – that's why this is a def instead of a val
  def currentLocaleOrdering: Ordering[String] = new Ordering[String] {
    private[this] val collator = Collator.getInstance(currentLocale)
    final def compare(a: String, b: String): Int = collator.compare(a, b)
  }

  def sortWithCurrentLocale[A](as: IndexedSeq[A], f: A => String): Vector[A] = { // much faster than as.sortBy(f)(currentLocaleOrdering)
    val collator = Collator.getInstance(currentLocale)
    val indexed = Array.tabulate(as.size)(n => (n, collator.getCollationKey(f(as(n)))))
    util.Arrays.sort(indexed, CollationKeyComparator)
    Vector.tabulate(indexed.size)(n => as(indexed(n)._1))
  }

  def preloadTransliterator(): Transliteration = transliteration

  lazy val transliteration = Transliteration.chooseImplementation()

  def transliteration(id: String) = Transliteration.chooseImplementation(id)

  def transliterate(str: String): String =
    if(utils.isTest) str
    else transliteration.transliterate(str).trim

  def indexing(locale: Locale = currentLocale): Indexing = FallbackIndexing.instance
}

object CollationKeyComparator extends Comparator[(Int, CollationKey)] {
  override def compare(lhs: (Int, CollationKey), rhs: (Int, CollationKey)): Int = lhs._2 compareTo rhs._2
}

trait LanguageTags {
  def languageTagOf(l: Locale): String
  def localeFor(languageTag: String): Option[Locale]
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
object AndroidLanguageTags {
  def create(implicit logTag: LogTag): LanguageTags = new LanguageTags {
    debug(l"using built-in Android language tag support")(logTag)
    def languageTagOf(l: Locale): String = l.toLanguageTag
    def localeFor(t: String): Option[Locale] = Try(Locale.forLanguageTag(t)).toOption
  }
}

trait Transliteration {
  def transliterate(s: String): String
}

object Transliteration extends DerivedLogTag {
  private val id = "Any-Latin; Latin-ASCII; Lower; [^\\ 0-9a-z] Remove"
  def chooseImplementation(id: String = id): Transliteration = {
    verbose(l"chooseImplementation: ${showString(id)}")
    ICU4JTransliteration.create(id)
  }
}

object ICU4JTransliteration {
  def create(id: String)(implicit logTag: LogTag): Transliteration = new Transliteration {
    debug(l"using ICU4J transliteration")(logTag)
    private val delegate = com.ibm.icu.text.Transliterator.getInstance(id)
    def transliterate(s: String): String = delegate.transliterate(s)
  }
}

trait Indexing {
  def labelFor(s: String): String
}

object FallbackIndexing extends DerivedLogTag {
  lazy val instance: Indexing = new Indexing {
    val blocks = Set(
      BASIC_LATIN,
      LATIN_1_SUPPLEMENT,
      LATIN_EXTENDED_A,
      LATIN_EXTENDED_B,
      LATIN_EXTENDED_ADDITIONAL,
      IPA_EXTENSIONS,
      SPACING_MODIFIER_LETTERS,
      PHONETIC_EXTENSIONS,
      SUPERSCRIPTS_AND_SUBSCRIPTS,
      NUMBER_FORMS,
      ALPHABETIC_PRESENTATION_FORMS,
      HALFWIDTH_AND_FULLWIDTH_FORMS)

    def isProbablyLatin(c: Int): Boolean = {
      val block = Character.UnicodeBlock.of(c)
      if (block eq null) false else blocks(block)
    }

    verbose(l"creating fallback indexing")

    @tailrec override def labelFor(s: String): String = {
      if (s.isEmpty) "#"
      else {
        val c = s.codePointAt(0)
        if (Character.isDigit(c)) "#"
        else if (c >= 'A' && c <= 'Z') String.valueOf(c.toChar)
        else if (c >= 'a' && c <= 'z') String.valueOf((c - 32).toChar)
        else if (isProbablyLatin(c)) labelFor(Locales.transliterate(s.substring(0, Character.charCount(c))))
        else "#"
      }
    }
  }
}
