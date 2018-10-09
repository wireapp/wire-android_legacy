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

@Ignore class MapSignalSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  implicit val ec: EventContext = EventContext.Global

  var received = Seq[Int]()
  val capture = (value: Int) => received = received :+ value

  before {
    received = Seq[Int]()
  }

  feature("Basic mapping") {
    scenario("Normal mapping") {
      val s = Signal(1)
      val m = s map (_ * 2)
      m(capture)

      Seq(2, 3, 1) foreach (s ! _)
      received shouldEqual Seq(2, 4, 6, 2)
    }

    scenario("Mapping nulls") {
      @volatile var vv: Option[String] = Some("invalid")
      val s = Signal("start")
      val m = s map (Option(_))
      m { vv = _ }
      vv shouldEqual Some("start")
      s ! "meep"
      vv shouldEqual Some("meep")
      s ! null
      vv shouldEqual None
      s ! "moo"
      vv shouldEqual Some("moo")
    }

    scenario("Chained mapping") {
      val s = Signal(1)
      val m = s map (_ * 2) map (_ * 3)
      m(capture)
      Seq(2, 3, 1) foreach (s ! _)
      received shouldEqual Seq(6, 12, 18, 6)
    }
  }

  feature("Subscriber lifecycle") {
    scenario("No subscribers will be left behind") {
      val s = Signal(1)
      val f = s map (_ * 2)
      val sub = f(capture)
      Seq(2, 3) foreach (s ! _)
      s.hasSubscribers shouldBe true
      f.hasSubscribers shouldBe true
      sub.destroy()
      s.hasSubscribers shouldBe false
      f.hasSubscribers shouldBe false
      s ! 4
      received shouldEqual Seq(2, 4, 6)
    }
  }

  feature("Auto-wiring") {
    scenario("wire and un-wire mapped signal wrapper") {
      lazy val s1 = new IntSignal(0)
      lazy val s = s1 map { _ => 1 }

      s1.isWired shouldBe false

      val o = s { _ => ()}

      s1.isWired shouldBe true

      o.disable()

      s1.isWired shouldBe false

      o.enable()
      s1.isWired shouldBe true

      o.destroy()
      s1.isWired shouldBe false
    }
  }
}
