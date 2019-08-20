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
package com.waz.service.conversation

import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, FeatureSpec, Ignore, Matchers}

import scala.collection.mutable.ListBuffer
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class SequenceWindowBufferSpec extends FeatureSpec with Matchers with BeforeAndAfter with GeneratorDrivenPropertyChecks {

  val windows = ListBuffer[(Long, Long)]()

  val closeMin = 2000L
  val closeMax = 2300L

  val sparseNumbers =  Gen.listOf[Long](Gen.choose(0, Long.MaxValue))
  val closeNumbers =  Gen.listOf[Long](Gen.choose(closeMin, closeMax))

  before {
    windows.clear()
  }

  def onWindowFound(start: Long, end: Long): Unit = {
    windows += start -> end
  }

  scenario("add 10000 sequential numbers without windows") {
    val buffer = new SequenceWindowBuffer(0, onWindowFound)

    for (i <- 1 to 10000) buffer.add(i)

    windows should be('empty)
  }

  scenario("init with max long") {
    val buffer = new SequenceWindowBuffer(Long.MaxValue, onWindowFound)
    buffer.add(Long.MaxValue)
    windows should be('empty)
  }

  scenario("add huge number, far off the buffer") {
    val buffer = new SequenceWindowBuffer(0, onWindowFound)

    buffer.add(Long.MaxValue)

    windows shouldEqual List(0 -> Long.MaxValue)
  }

  scenario("add sparse random ordered sequence numbers") {
    forAll(sparseNumbers) { numbers: List[Long] =>
      if (!numbers.isEmpty) {
        windows.clear()

        val ordered = numbers.sorted
        val buffer = new SequenceWindowBuffer(ordered.head, onWindowFound)
        ordered foreach buffer.add
        buffer.advanceTo(ordered.last)

        windows.toList shouldEqual optimalWindows(numbers)
      }
    }
  }

  scenario("add close random ordered sequence numbers") {
    forAll(closeNumbers) { numbers: List[Long] =>
      if (!numbers.isEmpty) {
        windows.clear()

        val ordered = numbers.sorted
        val buffer = new SequenceWindowBuffer(ordered.head, onWindowFound)
        ordered foreach buffer.add
        buffer.advanceTo(ordered.last)

        windows.toList shouldEqual optimalWindows(numbers)
      }
    }
  }

  scenario("add close unordered random numbers, all numbers will fit in the buffer") {
    forAll(closeNumbers) { numbers: List[Long] =>
      if (!numbers.isEmpty) {
        windows.clear()

        val buffer = new SequenceWindowBuffer(closeMin, onWindowFound)
        numbers foreach buffer.add

        windows should be('empty) // all numbers fit in the buffer so no windows should be found yet

        buffer.advanceTo(closeMax)

        windows.toList shouldEqual optimalWindows(closeMin :: closeMax :: numbers)
      }
    }
  }

  scenario("add sequential numbers with small random variation") {
    val buffer = new SequenceWindowBuffer(0, onWindowFound)
    var numbers = Set[Long](0)

    for (i <- 1 to 100000) {
      val n = i / 5 + Random.nextInt(250)
      numbers += n
      buffer.add(n)
    }
    buffer.advanceTo(numbers.max)

    info(s"windows count: ${windows.size}")

    windows.toList should be(optimalWindows(numbers.toList))
  }

  def optimalWindows(numbers: List[Long]) = numbers.sorted.iterator.sliding(2).filter(p => p.length == 2 && p(1) > p(0) + 1).map(p => p(0) -> p(1)).toList
}
