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

import org.scalatest.{FeatureSpec, Ignore, Matchers}

import scala.concurrent.duration._

class ExponentialBackoffSpec extends FeatureSpec with Matchers {

  scenario("max retries") {
    new ExponentialBackoff(1.second, 10.seconds).maxRetries shouldEqual 4
    new ExponentialBackoff(1.second, 100.seconds).maxRetries shouldEqual 7
    new ExponentialBackoff(1.second, 5.minutes).maxRetries shouldEqual 9
  }

  scenario("Test with large number of retries") {
    val eb = new ExponentialBackoff(1.second, 5.minutes)
    for (i <- 0 to 10000) {
      eb.delay(i).toMillis should be <= 5.minutes.toMillis + 1L
    }
  }

  scenario("Test with large timeouts") {
    val eb = new ExponentialBackoff(10.days, 365.days)
    for (i <- 0 to 10) {
      eb.delay(i).toMillis should be <= 365.days.toMillis + 1L
    }
  }
}
