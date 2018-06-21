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
package com.waz.model

import com.waz.model.EphemeralDuration._
import com.waz.specs.AndroidFreeSpec

import scala.concurrent.duration._

class EphemeralDurationSpec extends AndroidFreeSpec {

  scenario("display values") {

    MessageExpiry(0.seconds).display shouldEqual ((0, Second))

    //< Seconds
    MessageExpiry((1.second)).display                    shouldEqual ((1, Second))
    MessageExpiry((1.second + 300.nanoseconds)).display  shouldEqual ((1, Second))
    MessageExpiry((1.second + 300.microseconds)).display shouldEqual ((1, Second))
    MessageExpiry((1.second + 300.milliseconds)).display shouldEqual ((1, Second))

    //Seconds
    MessageExpiry((2.second)).display                    shouldEqual ((2, Second))
    MessageExpiry((2.second - 300.nanoseconds)).display  shouldEqual ((2, Second))
    MessageExpiry((2.second - 300.microseconds)).display shouldEqual ((2, Second))
    MessageExpiry((2.second - 300.milliseconds)).display shouldEqual ((2, Second))

    //Minutes
    MessageExpiry((1.minutes)).display               shouldEqual ((1, Minute))
    MessageExpiry((60.seconds)).display              shouldEqual ((1, Minute))

    MessageExpiry((1.minutes + 3.seconds)).display   shouldEqual ((1, Minute))
    MessageExpiry((1.minutes + 29.seconds)).display  shouldEqual ((1, Minute))
    MessageExpiry((1.minutes + 30.seconds)).display  shouldEqual ((2, Minute))
    MessageExpiry((2.minutes - 3.seconds)).display   shouldEqual ((2, Minute))

    //Hours
    MessageExpiry((1.hour)).display                  shouldEqual ((1, Hour))
    MessageExpiry((60.minutes)).display              shouldEqual ((1, Hour))

    MessageExpiry((1.hour + 3.seconds)).display      shouldEqual ((1, Hour))
    MessageExpiry((1.hour + 3.minutes)).display      shouldEqual ((1, Hour))
    MessageExpiry((1.hour + 29.minutes)).display     shouldEqual ((1, Hour))
    MessageExpiry((1.hour + 30.minutes)).display     shouldEqual ((2, Hour))
    MessageExpiry((2.hours - 3.seconds)).display     shouldEqual ((2, Hour))
    MessageExpiry((2.hours - 3.minutes)).display     shouldEqual ((2, Hour))


    //Days
    MessageExpiry((1.day)).display                   shouldEqual ((1, Day))
    MessageExpiry((24.hours)).display                shouldEqual ((1, Day))

    MessageExpiry((1.days + 3.seconds)).display      shouldEqual ((1, Day))
    MessageExpiry((1.days + 3.minutes)).display      shouldEqual ((1, Day))
    MessageExpiry((1.days + 3.hours)).display        shouldEqual ((1, Day))
    MessageExpiry((1.days + 11.hours)).display       shouldEqual ((1, Day))
    MessageExpiry((1.days + 12.hours)).display       shouldEqual ((2, Day))
    MessageExpiry((2.days - 3.seconds)).display     shouldEqual ((2, Day))
    MessageExpiry((2.days - 3.minutes)).display     shouldEqual ((2, Day))
    MessageExpiry((2.days - 3.hours)).display       shouldEqual ((2, Day))

    //Weeks
    MessageExpiry((7.days)).display                  shouldEqual ((1, Week))
    MessageExpiry((168.hours)).display               shouldEqual ((1, Week))

    MessageExpiry((7.days + 3.seconds)).display      shouldEqual ((1, Week))
    MessageExpiry((7.days + 3.minutes)).display      shouldEqual ((1, Week))
    MessageExpiry((7.days + 3.hours)).display        shouldEqual ((1, Week))
    MessageExpiry((7.days + 3.days)).display         shouldEqual ((1, Week))
    MessageExpiry((14.days - 3.seconds)).display     shouldEqual ((2, Week))
    MessageExpiry((14.days - 3.minutes)).display     shouldEqual ((2, Week))
    MessageExpiry((14.days - 3.hours)).display       shouldEqual ((2, Week))
    MessageExpiry((14.days - 3.days)).display        shouldEqual ((2, Week))

  }

}
