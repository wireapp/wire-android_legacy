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
import com.waz.ZLog.ImplicitTag._
import org.scalatest._

@Ignore class FilterSignalSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter with RobolectricTests with RobolectricUtils  {

  implicit val ec: EventContext = EventContext.Global

  feature("Filtering signals") {
    scenario("Value of a filtered signal") {
      val source = Signal(1)
      val chain = source.filter(_ % 2 == 0)
      chain.currentValue shouldBe empty
      source ! 2
      chain.currentValue.value shouldEqual 2
      source ! 3
      chain.currentValue shouldEqual None
      source ! 4
      chain.currentValue.value shouldEqual 4
    }

    scenario("Subscribing to a filtered signal") {
      val source = Signal(1)
      val chain = source.filter(_ % 2 == 0)
      val fan = Follower(chain).subscribed
      fan.lastReceived shouldBe empty
      source ! 2
      fan.received shouldEqual Seq(2)
      source ! 3
      fan.received shouldEqual Seq(2)
      chain.unsubscribeAll()
      fan.received shouldEqual Seq(2)
      fan.subscribed
      fan.received shouldEqual Seq(2)
      chain.unsubscribeAll()
      source ! 4
      fan.received shouldEqual Seq(2)
      fan.subscribed
      fan.received shouldEqual Seq(2, 4)
    }

    scenario("Possibly stale value after re-wiring") {
      val source = Signal(1)
      val chain = source.filter(_ % 2 == 0).map(identity)
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
