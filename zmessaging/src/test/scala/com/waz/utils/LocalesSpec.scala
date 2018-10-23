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

import java.util.Locale

import org.scalatest._

@Ignore class LocalesSpec extends FeatureSpec with Matchers with Inspectors with OptionValues with RobolectricTests {
  feature("BCP-47-compliant language tags") {
    scenario("Built-in") {
      lazy val bcp47 = AndroidLanguageTags.create("LocalesSpec")

      forEvery(availableLocale) { original =>
        bcp47.languageTagOf(bcp47.localeFor(bcp47.languageTagOf(original)).value) shouldEqual bcp47.languageTagOf(original)
      }
    }

    scenario("Fallback") {
      lazy val bcp47 = FallbackLanguageTags.create("LocalesSpec")

      forEvery(availableLocale) { locale =>
        bcp47.localeFor(bcp47.languageTagOf(locale)).value should have(
          'language (locale.getLanguage),
          'country (locale.getCountry))
      }
    }
  }

  feature("Indexing a string") {
    scenario("Empty name") {
      indexing.labelFor("") shouldEqual "#"
    }
    scenario("ASCII name") {
      indexing.labelFor("bugs bunny") shouldEqual "B"
      indexing.labelFor("Elmer Fudd") shouldEqual "E"
      indexing.labelFor("9tail Nala") shouldEqual "#"
    }
    scenario("Non-ASCII name") {
      indexing.labelFor("李淑蒙") shouldEqual "#"
      indexing.labelFor("Äda Ölwit") shouldEqual "A"
      indexing.labelFor("Иван Иванович Иванов") shouldEqual "#"
      indexing.labelFor("Анастасия Ивановна Иванова") shouldEqual "#"
    }
    scenario("Partial ASCII name") {
      indexing.labelFor("z李淑蒙") shouldEqual "Z"
      indexing.labelFor("Xäda Ölwit") shouldEqual "X"
      indexing.labelFor("李淑蒙y") shouldEqual "#"
      indexing.labelFor("Aнастасия Ивановна Иванова") shouldEqual "A"
    }
  }

  def availableLocale = Locale.getAvailableLocales.toSeq.filter(_.getDisplayName != "")
  lazy val indexing = Locales.indexing(Locale.UK)
}
