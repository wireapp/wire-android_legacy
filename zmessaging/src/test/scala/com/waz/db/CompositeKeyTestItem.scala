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

import com.waz.Generators._
import com.waz.model.UserId
import com.waz.utils.wrappers.DBCursor
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.threeten.bp.Instant

case class CompositeKeyTestItem(user: UserId, timestamp: Instant, meepIndex: Int, str: String, int: Int) {
  lazy val id = (user, timestamp, meepIndex)
}

object CompositeKeyTestItem {
  type Id = (UserId, Instant, Int)

  implicit object CompositeKeyTestItemDao extends Dao3[CompositeKeyTestItem, UserId, Instant, Int] {
    import Col._
    lazy val User = id[UserId]('user_id).apply(_.user)
    lazy val Timestamp = timestamp('timestamp)(_.timestamp)
    lazy val MeepIndex = int('meep_index)(_.meepIndex)
    lazy val Str = text('str)(_.str)
    lazy val Int = int('int)(_.int)

    override lazy val idCol = (User, Timestamp, MeepIndex)
    override lazy val table = new Table("CompositeKeyItems", User, Timestamp, MeepIndex, Str, Int)

    override def apply(implicit cursor: DBCursor): CompositeKeyTestItem = CompositeKeyTestItem(User, Timestamp, MeepIndex, Str, Int)
  }

  implicit val itemArbitrary = Arbitrary(for {
    u <- arbitrary[UserId]
    t <- arbitrary[Instant]
    m <- arbitrary[Int]
    s <- alphaNumStr
    i <- arbitrary[Int]
  } yield CompositeKeyTestItem(u, t, m, s, i))
}
