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

    EphemeralDuration(0.seconds) shouldEqual ((0, Second))

    //< Seconds
    EphemeralDuration((1.second))                    shouldEqual ((1, Second))
    EphemeralDuration((1.second + 300.nanoseconds))  shouldEqual ((1, Second))
    EphemeralDuration((1.second + 300.microseconds)) shouldEqual ((1, Second))
    EphemeralDuration((1.second + 300.milliseconds)) shouldEqual ((1, Second))

    //Seconds
    EphemeralDuration((2.second))                    shouldEqual ((2, Second))
    EphemeralDuration((2.second - 300.nanoseconds))  shouldEqual ((2, Second))
    EphemeralDuration((2.second - 300.microseconds)) shouldEqual ((2, Second))
    EphemeralDuration((2.second - 300.milliseconds)) shouldEqual ((2, Second))

    //Minutes
    EphemeralDuration((1.minutes))               shouldEqual ((1, Minute))
    EphemeralDuration((60.seconds))              shouldEqual ((1, Minute))

    EphemeralDuration((1.minutes + 3.seconds))   shouldEqual ((1, Minute))
    EphemeralDuration((1.minutes + 29.seconds))  shouldEqual ((1, Minute))
    EphemeralDuration((1.minutes + 30.seconds))  shouldEqual ((2, Minute))
    EphemeralDuration((2.minutes - 3.seconds))   shouldEqual ((2, Minute))

    //Hours
    EphemeralDuration((1.hour))                  shouldEqual ((1, Hour))
    EphemeralDuration((60.minutes))              shouldEqual ((1, Hour))

    EphemeralDuration((1.hour + 3.seconds))      shouldEqual ((1, Hour))
    EphemeralDuration((1.hour + 3.minutes))      shouldEqual ((1, Hour))
    EphemeralDuration((1.hour + 29.minutes))     shouldEqual ((1, Hour))
    EphemeralDuration((1.hour + 30.minutes))     shouldEqual ((2, Hour))
    EphemeralDuration((2.hours - 3.seconds))     shouldEqual ((2, Hour))
    EphemeralDuration((2.hours - 3.minutes))     shouldEqual ((2, Hour))


    //Days
    EphemeralDuration((1.day))                   shouldEqual ((1, Day))
    EphemeralDuration((24.hours))                shouldEqual ((1, Day))

    EphemeralDuration((1.days + 3.seconds))      shouldEqual ((1, Day))
    EphemeralDuration((1.days + 3.minutes))      shouldEqual ((1, Day))
    EphemeralDuration((1.days + 3.hours))        shouldEqual ((1, Day))
    EphemeralDuration((1.days + 11.hours))       shouldEqual ((1, Day))
    EphemeralDuration((1.days + 12.hours))       shouldEqual ((2, Day))
    EphemeralDuration((2.days - 3.seconds))     shouldEqual ((2, Day))
    EphemeralDuration((2.days - 3.minutes))     shouldEqual ((2, Day))
    EphemeralDuration((2.days - 3.hours))       shouldEqual ((2, Day))

    //Weeks
    EphemeralDuration((7.days))                  shouldEqual ((1, Week))
    EphemeralDuration((168.hours))               shouldEqual ((1, Week))

    EphemeralDuration((7.days + 3.seconds))      shouldEqual ((1, Week))
    EphemeralDuration((7.days + 3.minutes))      shouldEqual ((1, Week))
    EphemeralDuration((7.days + 3.hours))        shouldEqual ((1, Week))
    EphemeralDuration((7.days + 3.days))         shouldEqual ((1, Week))
    EphemeralDuration((14.days - 3.seconds))     shouldEqual ((2, Week))
    EphemeralDuration((14.days - 3.minutes))     shouldEqual ((2, Week))
    EphemeralDuration((14.days - 3.hours))       shouldEqual ((2, Week))
    EphemeralDuration((14.days - 3.days))        shouldEqual ((2, Week))


    //Years (defined to be 365 days)
    EphemeralDuration((365.days + 3.seconds))       shouldEqual ((1, Year))
    EphemeralDuration((365.days + 3.minutes))       shouldEqual ((1, Year))
    EphemeralDuration((365.days + 3.hours))         shouldEqual ((1, Year))
    EphemeralDuration((365.days + 3.days))          shouldEqual ((1, Year))
    EphemeralDuration((365.days + 182.days))        shouldEqual ((1, Year))
    EphemeralDuration(((365 * 2).days - 3.seconds)) shouldEqual ((2, Year))
    EphemeralDuration(((365 * 2).days - 3.minutes)) shouldEqual ((2, Year))
    EphemeralDuration(((365 * 2).days - 3.hours))   shouldEqual ((2, Year))
    EphemeralDuration(((365 * 2).days - 3.days))    shouldEqual ((2, Year))
    EphemeralDuration(((365 * 2).days - 182.days))  shouldEqual ((2, Year))

    //Changing granularity
    EphemeralDuration(1000000000L.nanoseconds)                          shouldEqual ((1, Second))
    EphemeralDuration((1000000000L * 60).nanoseconds)                   shouldEqual ((1, Minute))
    EphemeralDuration((1000000000L * 60 * 60).nanoseconds)              shouldEqual ((1, Hour))
    EphemeralDuration((1000000000L * 60 * 60 * 24).nanoseconds)         shouldEqual ((1, Day))
    EphemeralDuration((1000000000L * 60 * 60 * 24 * 7).nanoseconds)     shouldEqual ((1, Week))
    EphemeralDuration((1000000000L * 60 * 60 * 24 * 365).nanoseconds)   shouldEqual ((1, Year))

  }

}
