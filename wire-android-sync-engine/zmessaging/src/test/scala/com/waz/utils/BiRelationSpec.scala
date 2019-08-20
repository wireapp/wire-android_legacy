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

import org.junit.runner.RunWith
import org.scalacheck.Gen.resultOf
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FeatureSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class BiRelationSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks {

  scenario("Adding and removing single pairs") {
    val a = BiRelation.empty[Int, String]

    a.contains(1, "one") shouldBe false
    a.containsLeft(1) shouldBe false
    a.containsRight("one") shouldBe false
    a.aftersets shouldBe empty
    a.foresets shouldBe empty
    a.afterset(1) shouldEqual Set.empty[String]
    a.foreset("one") shouldEqual Set.empty[Int]

    val b = a + (1, "one")

    b.contains(1, "one") shouldBe true
    b.containsLeft(1) shouldBe true
    b.containsRight("one") shouldBe true
    b.afterset(1) shouldEqual Set("one")
    b.foreset("one") shouldEqual Set(1)

    (b - (1, "one")) shouldEqual a
    (b + (1, "one")) shouldEqual b

    val c = b + (1, "eins")

    c.contains(1, "one") shouldBe true
    c.contains(1, "eins") shouldBe true
    c.containsLeft(1) shouldBe true
    c.containsRight("one") shouldBe true
    c.containsRight("eins") shouldBe true
    c.afterset(1) shouldEqual Set("one", "eins")
    c.foreset("one") shouldEqual Set(1)
    c.foreset("eins") shouldEqual Set(1)

    (c - (1, "eins")) shouldEqual b
    (c - (1, "one") - (1, "eins")) shouldEqual a
    (c - (1, "eins") - (1, "one")) shouldEqual a
    (c + (1, "one")) shouldEqual c

    val d = c + (2, "two")
    d.contains(1, "one") shouldBe true
    d.contains(1, "eins") shouldBe true
    d.containsLeft(1) shouldBe true
    d.containsLeft(2) shouldBe true
    d.containsRight("one") shouldBe true
    d.containsRight("eins") shouldBe true
    d.containsRight("two") shouldBe true
    d.afterset(1) shouldEqual Set("one", "eins")
    d.afterset(2) shouldEqual Set("two")
    d.foreset("one") shouldEqual Set(1)
    d.foreset("eins") shouldEqual Set(1)
    d.foreset("two") shouldEqual Set(2)

    (d - (1, "uno")) shouldEqual d

    val e = d + (-1, "one")
    e.contains(1, "one") shouldBe true
    e.contains(1, "eins") shouldBe true
    e.contains(-1, "eins") shouldBe true
    e.containsLeft(1) shouldBe true
    e.containsLeft(2) shouldBe true
    e.containsLeft(-1) shouldBe true
    e.containsRight("one") shouldBe true
    e.containsRight("eins") shouldBe true
    e.containsRight("two") shouldBe true
    e.afterset(1) shouldEqual Set("one", "eins")
    e.afterset(2) shouldEqual Set("two")
    e.foreset("one") shouldEqual Set(1, -1)
    e.foreset("eins") shouldEqual Set(1)
    e.foreset("two") shouldEqual Set(2)

    (e - (-1, "one")) shouldEqual d
  }

  feature("Batch operations") {
    scenario("Create") {
      forAll { (x: Set[(L, R)]) =>
        val x1 = BiRelation(x)
        x1 shouldEqual x.foldLeft(BiRelation.empty[L, R]) { case (accu, (a, b)) => accu + (a, b) }
        val x2 = x1.foldLeft(x1) { case (accu, (a, b)) => accu - (a, b) }
        x2 shouldEqual BiRelation.empty[L, R]
        x2 shouldBe empty
      }
    }

    scenario("Add and remove from afterset") {
      forAll { (x: BiRelation[L, R], a: L, bs: Set[R]) =>
        BiRelation.empty[L, R].addToAfterset(a, bs).removeFromAfterset(a, bs) shouldBe empty
        x.addToAfterset(a, bs).addToAfterset(a, bs) shouldEqual x.addToAfterset(a, bs)
        x.removeFromAfterset(a, bs).addToAfterset(a, bs) shouldEqual x.addToAfterset(a, bs)
        aftersets(x).foldLeft(x) { case (accu, (a, bs)) => accu.removeFromAfterset(a, bs) } shouldBe empty
      }
    }

    scenario("Add and remove from foreset") {
      forAll { (x: BiRelation[L, R], b: R, as: Set[L]) =>
        BiRelation.empty[L, R].addToForeset(b, as).removeFromForeset(b, as) shouldBe empty
        x.addToForeset(b, as).addToForeset(b, as) shouldEqual x.addToForeset(b, as)
        x.removeFromForeset(b, as).addToForeset(b, as) shouldEqual x.addToForeset(b, as)
        foresets(x).foldLeft(x) { case (accu, (b, as)) => accu.removeFromForeset(b, as) } shouldBe empty
      }
    }

    scenario("Remove left") {
      forAll { (x: BiRelation[L, R], a: L) =>
        x.removeLeft(a).containsLeft(a) shouldBe false
        x.aftersets.keys.foldLeft(x)((accu, a) => accu.removeLeft(a)) shouldBe empty
      }
    }

    scenario("Remove right") {
      forAll { (x: BiRelation[L, R], b: R) =>
        x.removeRight(b).containsRight(b) shouldBe false
        x.aftersets.keys.foldLeft(x)((accu, a) => accu.removeLeft(a)) shouldBe empty
      }
    }
  }

  // small domain and codomain to make collisions likely
  implicit lazy val arbL: Arbitrary[L] = Arbitrary(Gen.choose(1, 5) map L)
  implicit lazy val arbR: Arbitrary[R] = Arbitrary(Gen.choose('a', 'g') map R)

  implicit def arbBiRelation[A, B](implicit arb: Arbitrary[Set[(A, B)]]): Arbitrary[BiRelation[A, B]] = Arbitrary(resultOf[Set[(A, B)], BiRelation[A, B]](BiRelation.apply[A, B]))

  def aftersets(s: TraversableOnce[(L, R)]): Map[L, Set[R]] = s.toStream.groupBy(_._1).map { case (k, v) => (k, v.map(_._2).toSet) }
  def foresets(s: TraversableOnce[(L, R)]): Map[R, Set[L]] = s.toStream.groupBy(_._2).map { case (k, v) => (k, v.map(_._1).toSet) }

  case class L(l: Int)
  case class R(r: Char)
}
