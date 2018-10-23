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
package com.waz

import com.waz.utils.returning
import org.scalatest.{FeatureSpec, Matchers}

class MacroSpec extends FeatureSpec with Matchers {
  feature("Kestrel (K combinator)") {
    scenario("Anonymous function") {
      returning(Cell())((d: Cell) => d.num = 42L) shouldEqual Cell(42L)
    }

    scenario("Anonymous function, no type annotation") {
      returning(Cell())(d => d.num = 42L) shouldEqual Cell(42L)
    }

    scenario("Anonymous function, unnamed parameter") {
      returning(Cell())(_.num = 42L) shouldEqual Cell(42L)
    }

    scenario("Lifted method") {
      returning(Cell())(set42) shouldEqual Cell(42L)
    }

    scenario("Partial application") {
      returning(Cell())(set41Add(_, 1L)) shouldEqual Cell(42L)
    }

    scenario("Currying") {
      returning(Cell())(set21Mult(2L)) shouldEqual Cell(42L)
    }

    scenario("Function def") {
      returning(Cell())(set42f) shouldEqual Cell(42L)
    }

    scenario("Function val") {
      returning(Cell())(set42fv) shouldEqual Cell(42L)
    }

    scenario("Preceding block") {
      var n = 0
      returning(Cell()) { n += 1; d => { d.num = 41L; d.num += 1 } } shouldEqual Cell(42L)
      n shouldEqual 1
    }

    scenario("Scope") {
      val x = Cell()
      returning(Cell())(x => x.num = 42L) shouldEqual Cell(42L)
      x.num shouldEqual 0L
    }

    scenario("Init block") {
      var n = 0
      returning {
        n += 1
        Cell(n)
      }(_.num += 41L) shouldEqual Cell(42L)
      n shouldEqual 1
    }
  }

  def set42(d: Cell) = d.num = 42
  def set41Add(d: Cell, n: Long) = d.num = (41L + n)
  def set21Mult(n: Long)(d: Cell) = d.num = (21L * n)
  def set42f = (_: Cell).num = 42L
  val set42fv = (_: Cell).num = 42L
}

case class Cell(var num: Long = 0L)
