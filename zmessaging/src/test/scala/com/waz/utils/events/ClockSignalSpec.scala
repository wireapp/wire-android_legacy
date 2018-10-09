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
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest._
import org.threeten.bp.Instant, Instant.now

import scala.concurrent.duration._

@Ignore class ClockSignalSpec extends FeatureSpec with Matchers with OptionValues with RobolectricTests with RobolectricUtils {

  implicit val tolerance = 100.millis.tolerance

  scenario("Subscribe, unsubscribe, re-subscribe") {
    val signal = ClockSignal(1.millis)

    val v1 = signal.value
    v1 should beRoughly(now)

    idle(200.millis)
    signal.value shouldEqual v1

    val sub1 = signal.sink
    sub1.current should beRoughly(now)

    idle(200.millis)
    signal.value should beRoughly(now)
    sub1.current should beRoughly(now)

    sub1.unsubscribe

    val v2 = signal.value
    val v3 = sub1.current

    idle(200.millis)

    signal.value shouldEqual v2
    sub1.current shouldEqual v3

    val sub2 = signal.sink
    sub1.current shouldEqual v3
    sub2.current should beRoughly(now)

    idle(200.millis)
    signal.value should beRoughly(now)
    sub1.current shouldEqual v3
    sub2.current should beRoughly(now)
  }
}
