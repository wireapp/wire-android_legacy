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

import com.waz.Food
import com.waz.utils.EnumCodec.injective
import com.waz.Food._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FeatureSpec, Inspectors, Matchers}

@RunWith(classOf[JUnitRunner])
class EnumCodecSpec extends FeatureSpec with Matchers with Inspectors {

  scenario("Decode is the inverse of encode") {
    val FoodCodec = injective[Food, String] {
      case APPLES => "Apples"
      case ORANGES => "Oranges"
      case COOKIES => "Cookies"
      case COFFEE => "Coffee"
    }

    forAll(Food.values.toSeq) { f => FoodCodec.decode(FoodCodec.encode(f)) shouldBe f }
  }

  scenario("Codecs created from equivalent functions are compatible") {
    val FoodCodec1 = injective[Food, String] {
      case f if f.name.startsWith("A") => "Apples"
      case f if f.name.startsWith("O") => "Oranges"
      case f if f.name.startsWith("COO") => "Cookies"
      case _ => "Coffee"
    }

    val FoodCodec2 = injective[Food, String] {
      case APPLES => "Apples"
      case ORANGES => "Oranges"
      case COOKIES => "Cookies"
      case COFFEE => "Coffee"
    }

    forAll(Food.values.toSeq) { f => FoodCodec1.decode(FoodCodec2.encode(f)) shouldBe f }
    forAll(Food.values.toSeq) { f => FoodCodec2.decode(FoodCodec1.encode(f)) shouldBe f }
  }

  scenario("Creating a codec from a non-injective function is not possible") {
    an [IllegalArgumentException] should be thrownBy injective[Food, String] {
      case APPLES | ORANGES => "Apples"
      case COOKIES => "Cookies"
      case COFFEE => "Coffee"
    }
  }

  scenario("Creating a codec from a partial function is not possible") {
    a [MatchError] should be thrownBy injective[Food, String] {
      case x if x == APPLES => "Apples"
    }
  }

  scenario("Null is not in allowed in the function's image") {
    an [IllegalArgumentException] should be thrownBy injective[Food, String] {
      case APPLES => "Apples"
      case ORANGES => null
      case COOKIES => "Cookies"
      case COFFEE => "Coffee"
    }
  }
}
