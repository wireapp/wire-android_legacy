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

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables
import org.scalatest.{FeatureSpec, Ignore, Matchers}

class NamePartsSpec extends FeatureSpec with Matchers with Tables {

  val muhammadFull = "محمد بن سعيد بن عبد العزيز الفلسطيني"
  val muhammadFirst = "محمد"
  val muhammadInit = "ما"
  val abdullahFull = "عبد الله الثاني بن الحسين"
  val abdullahFirst = "عبد الله"
  val abdullahInit = "عا"
  val amatFull = "امه العليم السوسوه"
  val amatFirst = "امه العليم"
  val amatInit = "اا"
  val habibFull = "حبيبا لله الثاني بن الحسين"
  val habibFirst = "حبيبا لله"
  val habibInit = "حا"

  val names = Table(
    ("name", "full name", "first name", "first with initial of last", "initials"),
    ("John Evans", "John Evans", "John", "John E", "JE"),
    ("John Anthony Evans", "John Anthony Evans", "John", "John E", "JE"),
    ("John", "John", "John", "John", "J"),
    (" John", " John", "John", "John", "J"),
    ("Vincent de Gryuter", "Vincent de Gryuter", "Vincent", "Vincent G", "VG"),
    ("Vincent de gryuter", "Vincent de gryuter", "Vincent", "Vincent g", "VG"),
    ("L. L. Cool J", "L. L. Cool J", "L.", "L. J", "LJ"),
    ("The Amazing Kangaroo", "The Amazing Kangaroo", "The", "The K", "TK"),
    ("Andrea:) 900973", "Andrea:) 900973", "Andrea:)", "Andrea:)", "A"),
    ("377 [808]", "377 [808]", "377", "377", ""),
    ("1234", "1234", "1234", "1234", ""),
    (muhammadFull, muhammadFull, muhammadFirst, muhammadFull, muhammadInit),
    (abdullahFull, abdullahFull, abdullahFirst, abdullahFull, abdullahInit),
    (amatFull, amatFull, amatFirst, amatFull, amatInit),
    (habibFull, habibFull, habibFirst, habibFull, habibInit),
    ("李淑蒙", "李淑蒙", "李淑蒙", "李淑蒙", "李淑"),
    ("李", "李", "李", "李", "李"),
    ("千早正木", "千早正木", "千早正木", "千早正木", "千早"),
    ("李淑蒙(shumeng)", "李淑蒙(shumeng)", "李淑蒙(shumeng)", "李淑蒙(shumeng)", "李淑"),
    ("李淑蒙 (shumeng)", "李淑蒙 (shumeng)", "李淑蒙", "李淑蒙", "李淑")
  )

  feature("Names") {
    scenario("Splitting up names into parts") {
      forAll(names) { (raw: String, fullExp: String, firstExp: String, firstWithInitialExp: String, initialsExp: String) =>
        NameParts.parseFrom(raw) match {
          case NameParts(full, first, firstWithInitial, initials) =>
            full shouldEqual fullExp
            first shouldEqual firstExp
            firstWithInitial shouldEqual firstWithInitialExp
            initials shouldEqual initialsExp

          case _ => fail(s"The name '$raw' should be parsable.")
        }
      }
    }

    scenario("initials for multipart name") {
      NameParts.parseFrom("some other user") shouldEqual NameParts("some other user", "some", "some u", "SU")
    }
  }
}
