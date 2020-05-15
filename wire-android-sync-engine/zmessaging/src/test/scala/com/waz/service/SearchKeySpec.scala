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

import org.junit.runner.RunWith
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, TableDrivenPropertyChecks}

import scala.language.postfixOps

//TODO: Remove Robolectric dependencies
@RunWith(classOf[JUnitRunner])
class SearchKeySpec extends FeatureSpec with GeneratorDrivenPropertyChecks with TableDrivenPropertyChecks
  with Matchers with RobolectricTests {

  feature("Search key normalization") {
    scenario("building search keys from known examples") {
      val examples = Table(
        ("input"                       , "expected output"                ),
        //-----------------------------,-----------------------------------
        ("asd ÖòÄáÜß ®†€çäÀöüÚ åÅ 'Eé" , "asd ooaauss rcaaouu aa ee"      ),
        ("Ыам кевёбюж"                 , "yam kevebuz"                    ),
        ("ἔχων μέλανος οἴνοιο"         , "echon melanos oinoio"           ),
        ("李淑蒙"                       , "li shu meng"                    ),
        ("千早正木"                     , "qian zao zheng mu"              ),
        ("悠久한 歷史와 傳統에"           , "you jiuhan li shiwa chuan tonge"),
        ("กุมภาพันธ์"                     , "kumphaphanth"                   ),
        ("カタカナ"                     , "katakana"                       ),
        ("ひらがな"                     , "hiragana"                       ),
        ("امه العليم"                  , "amh allym"                      ),
        ("मानक हिन्दी"                    , "manaka hindi"                   ),
        ("הירשמו כעת לכנס"              , "hyrsmw kt lkns"                 ),
        ("လူတိုင\u103Aးသည\u103A"   , "" /* Burmese script n/a */      )
      )

      forAll (examples) ((input, expectedOutput) => SearchKey(input).asciiRepresentation should equal (expectedOutput))
    }

    scenario("building a search key from random input") {
      forAll (minSuccessful(1000)) { input: String =>
        val ascii = SearchKey(input).asciiRepresentation

        all(ascii) should ((be >= 'a' and be <= 'z') or (be >= '0' and be <= '9') or equal(' '))
      }
    }

    scenario("building a search key from a search key") {
      forAll { key: SearchKey =>
        SearchKey(key.asciiRepresentation) should equal (key)
      }
    }

    scenario("matching a search key against another") {
      val phrases = Set("a fantastic phrase", "fan art", "something a fan drew", "infanity if fuch a ftupid fing", "that was infantile")
      val prefix = SearchKey(" fÄN ")

      phrases map SearchKey filter (phrase => prefix isAtTheStartOfAnyWordIn phrase) map (_ asciiRepresentation) should contain theSameElementsAs Seq(
        "a fantastic phrase", "fan art", "something a fan drew"
      )
    }

    scenario("the empty string always matches") {
      forAll { key: SearchKey =>
        SearchKey.Empty.isAtTheStartOfAnyWordIn(key) shouldBe true
      }
    }
  }

  implicit lazy val arbSearchKey: Arbitrary[SearchKey] = Arbitrary(Gen.resultOf(SearchKey))
}
