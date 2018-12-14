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
package com.waz.db

import com.waz.model.Uid
import com.waz.utils.wrappers.DBCursor
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary._

case class TestItem(id: Uid, str: String, int: Int)

object TestItem {

  implicit object TestItemDao extends Dao[TestItem, Uid] {
    import Col._
    val Id = uid('_id, "PRIMARY KEY")(_.id)
    val Str = text('str)(_.str)
    val Int = int('int)(_.int)

    override val idCol = Id
    override val table = new Table("Items", Id, Str, Int)

    override def apply(implicit cursor: DBCursor): TestItem = TestItem(Id, Str, Int)
  }

  // limited string generator for our tests, full generator caused errors in db some chars were stripped
  val stringGen = Gen.listOf(Gen.choose[Char](30, 0xff)).map(_.mkString) // TODO: replace that with full string generator and test on device to see if the problems happen there to

  implicit val itemArbitrary = Arbitrary(for {s <- stringGen; i <- arbitrary[Int] } yield TestItem(Uid(), s, i))
}
