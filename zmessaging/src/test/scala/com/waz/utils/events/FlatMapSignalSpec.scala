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

@Ignore class FlatMapSignalSpec extends FeatureSpec with OptionValues with Matchers with BeforeAndAfter with RobolectricTests {
  implicit val ec: EventContext = EventContext.Global

  var received = Vector.empty[Int]
  val capture = (value: Int) => received :+= value

  before {
    received = Vector.empty[Int]
  }

  feature("Basic flatmapping") {
    scenario("Normal flatmapping") {
      val s = Signal(0)
      val s1 = Signal(1)
      val s2 = Signal(2)

      val fm = s flatMap Seq(s1, s2)
      fm(capture)

      fm.value shouldEqual Some(1)
      s ! 1
      fm.value shouldEqual Some(2)
      s1 ! 3
      fm.value shouldEqual Some(2)
      s2 ! 4
      fm.value shouldEqual Some(4)
      received shouldEqual Seq(1, 2, 4)
    }

    scenario("Chained flatmapping") {
      val s = Seq.fill(6)(Signal(0))

      val fm = s(0) flatMap Seq(s(1), s(2)) flatMap Seq(s(3), s(4), s(5))
      fm(capture)

      s(5) ! 5
      s(2) ! 2
      s(0) ! 1

      fm.value shouldEqual Some(5)
      received shouldEqual Seq(0, 5)
    }

    scenario("FlatMapping an empty signal") {
      val signal = Signal[Int]()
      val chain = signal.flatMap(_ => Signal(42))

      chain.currentValue shouldBe empty
      signal ! Int.MaxValue
      chain.currentValue.value shouldEqual 42
    }

    scenario("FlatMapping to an empty signal") {
      val signal = Signal(0)
      val signalA = Signal[String]()
      val signalB = Signal[String]()
      val chain = signal.flatMap(n => if (n % 2 == 0) signalA else signalB)
      val fan = Follower(chain).subscribed
      chain.currentValue shouldBe empty
      fan.received shouldBe empty

      signalA ! "a"
      chain.currentValue.value shouldEqual "a"
      fan.received shouldEqual Seq("a")

      signal ! 1
      chain.currentValue shouldEqual None
      fan.received shouldEqual Seq("a")

      signalA ! "aa"
      chain.currentValue shouldEqual None
      fan.received shouldEqual Seq("a")

      signalB ! "b"
      chain.currentValue.value shouldEqual "b"
      fan.received shouldEqual Seq("a", "b")
    }
  }

  feature("Subscriber lifecycle") {
    scenario("No subscribers will be left behind") {
      val s = Signal(0)
      val s1 = Signal(1)
      val s2 = Signal(2)

      val fm = s flatMap Seq(s1, s2)
      val sub = fm(capture)

      s1 ! 3
      s2 ! 4

      s.hasSubscribers shouldEqual true
      s1.hasSubscribers shouldEqual true
      s2.hasSubscribers shouldEqual false
      fm.hasSubscribers shouldEqual true

      sub.destroy()
      s.hasSubscribers shouldEqual false
      s1.hasSubscribers shouldEqual false
      s2.hasSubscribers shouldEqual false
      fm.hasSubscribers shouldEqual false

      s1 ! 5
      s ! 1
      s2 ! 6
      received shouldEqual Seq(1, 3)
    }
  }

  feature("Auto-wiring") {
    scenario("wire and un-wire both source signals") {
      val s1 = new IntSignal
      val s2 = new IntSignal
      val s = s1.flatMap { _ => s2 }

      (s1.isWired, s2.isWired) shouldEqual (false, false)

      val o = s { _ => ()}

      (s1.isWired, s2.isWired) shouldEqual (true, true)

      o.disable()
      (s1.isWired, s2.isWired) shouldEqual (false, false)
    }

    scenario("un-wire discarded signal on change") {
      val s = new IntSignal(0)
      val s1 = new IntSignal(1)
      val s2 = new IntSignal(2)

      val fm = s flatMap Seq(s1, s2)
      val o = fm( _ => ())

      (s.isWired, s1.isWired, s2.isWired) shouldEqual (true, true, false)

      s ! 1
      (s.isWired, s1.isWired, s2.isWired) shouldEqual (true, false, true)

      o.destroy()
      (s.isWired, s1.isWired, s2.isWired) shouldEqual (false, false, false)
    }

    scenario("update value when wired") {
      val s = new IntSignal(0)
      val fm = s.flatMap(Signal(_))

      s.value shouldEqual Some(0)
      fm.value shouldEqual None

      s ! 1
      s.value shouldEqual Some(1)
      fm.value shouldEqual None // not updated because signal is not autowired
      val o = fm( _ => ())

      fm.value shouldEqual Some(1) // updated when wiring
    }

    scenario("possibly stale value after re-wiring") {
      val source = Signal(1)
      val chain = source.flatMap(n => if (n % 2 == 0) Signal(n) else Signal[Int]()).map(identity)
      val fan = Follower(chain).subscribed
      source ! 2
      fan.received shouldEqual Seq(2)
      chain.unsubscribeAll()

      (3 to 7) foreach source.!

      chain.currentValue shouldEqual None
      fan.subscribed
      fan.received shouldEqual Seq(2)
      source ! 8
      fan.received shouldEqual Seq(2, 8)
    }
  }
}
