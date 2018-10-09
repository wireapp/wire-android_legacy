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

import com.waz.RobolectricUtils
import org.scalatest._
import com.waz.ZLog.ImplicitTag._

@Ignore class EmptySignalSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter with RobolectricTests with RobolectricUtils  {

  implicit val ec: EventContext = EventContext.Global

  feature("Uninitialized signals") {
    scenario("Value of an uninitialized signal") {
      val signal = Signal[Int]()
      signal.currentValue shouldBe empty
      signal ! 1
      signal.currentValue.value shouldEqual 1
    }

    scenario("Subscribing to an uninitialized signal") {
      val signal = Signal[Int]()
      val fan = Follower(signal).subscribed
      fan.lastReceived shouldBe empty
      signal ! 1
      fan.lastReceived.value shouldEqual 1
    }

    scenario("Mapping an uninitialized signal") {
      val signal = Signal[Int]()
      val chain = signal.map(_ + 42)
      chain.currentValue shouldBe empty
      signal ! 1
      chain.currentValue.value shouldEqual 43
    }

    scenario("Subscribing to a mapped but uninitialized signal") {
      val signal = Signal[Int]()
      val chain = signal.map(_ + 42)
      val fan = Follower(chain).subscribed
      fan.lastReceived shouldBe empty
      signal ! 1
      fan.lastReceived.value shouldEqual 43
    }
  }

  feature("Combining an empty signal with another signal") {
    scenario("Value of a flatMapped signal") {
      val signalA = Signal(1)
      val signalB = Signal[Int]()
      val chain = signalA.flatMap(a => signalB.map(b => a + b) )
      chain.currentValue shouldBe empty
      signalB ! 42
      chain.currentValue.value shouldEqual 43
    }

    scenario("Subscribing to a flatMapped signal") {
      val signalA = Signal(1)
      val signalB = Signal[Int]()
      val chain = signalA.flatMap(a => signalB.map(b => a + b))
      val fan = Follower(chain).subscribed
      fan.lastReceived shouldBe empty
      signalB ! 42
      fan.lastReceived.value shouldEqual 43
    }

    scenario("Zipping with an empty signal") {
      val signalA = Signal(1)
      val signalB = Signal[String]()
      val chain = signalA.zip(signalB)
      val fan = Follower(chain).subscribed
      fan.lastReceived shouldBe empty
      signalB ! "one"
      fan.lastReceived.value shouldEqual (1, "one")
    }

    scenario("Combining with an empty signal") {
      val signalA = Signal(1)
      val signalB = Signal[Int]()
      val chain = signalA.combine(signalB)(_ + _)
      val fan = Follower(chain).subscribed
      fan.lastReceived shouldBe empty
      signalB ! 42
      fan.lastReceived.value shouldEqual 43
    }

    scenario("Map after filter") {
      val signalA = Signal(1)
      val chain = signalA.filter(_ % 2 == 0).map(_ + 42)
      val fan = Follower(chain).subscribed
      chain.currentValue shouldBe empty
      fan.received shouldBe empty

      signalA ! 2
      chain.currentValue shouldEqual Some(44)
      fan.received shouldEqual Seq(44)

      signalA ! 3
      chain.currentValue shouldEqual None
      fan.received shouldEqual Seq(44)

      signalA ! 4
      chain.currentValue shouldEqual Some(46)
      fan.received shouldEqual Seq(44, 46)
    }
  }
}
