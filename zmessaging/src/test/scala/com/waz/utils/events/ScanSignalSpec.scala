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
package com.waz.utils.events

import org.scalatest._
import com.waz.ZLog.ImplicitTag._

@Ignore class ScanSignalSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  implicit val ec: EventContext = EventContext.Global

  var received = Seq[Int]()
  val capture = (value: Int) => received = received :+ value

  before {
    received = Seq[Int]()
  }

  feature("Basic scanning") {
    scenario("Normal scanning") {
      val s = Signal(1)
      val scanned = s.scan(0)(_ + _)
      scanned.value shouldEqual Some(0)

      scanned(capture)
      scanned.value shouldEqual Some(1)
      Seq(2, 3, 1) foreach (s ! _)

      received shouldEqual Seq(1, 3, 6, 7)
      scanned.value shouldEqual Some(7)
    }

    scenario("disable autowiring when fetching current value") {
      val s = Signal(1)
      val scanned = s.scan(0)(_ + _)
      scanned.currentValue shouldEqual Some(1)

      Seq(2, 3, 1) foreach (s ! _)
      scanned.value shouldEqual Some(7)
    }

    scenario("Chained scanning") {
      val s = Signal(1)
      val scanned = s .scan(0)(_ + _) .scan(1)(_ * _)
      scanned.currentValue shouldEqual Some(1)

      scanned(capture)
      Seq(2, 3, 1) foreach (s ! _)

      scanned.currentValue shouldEqual Some(3 * 6 * 7)
      received shouldEqual Seq(1, 3, 3 * 6, 3 * 6 * 7)
    }
  }

  feature("Subscriber lifecycle") {
    scenario("No subscribers will be left behind") {
      val s = Signal(1)
      val scanned = s.scan(0)(_ + _)
      val sub = scanned(capture)
      Seq(2, 3) foreach (s ! _)
      s.hasSubscribers shouldEqual true
      scanned.hasSubscribers shouldEqual true
      sub.destroy()
      s.hasSubscribers shouldEqual false
      scanned.hasSubscribers shouldEqual false
      s ! 4
      received shouldEqual Seq(1, 3, 6)
    }
  }
}
