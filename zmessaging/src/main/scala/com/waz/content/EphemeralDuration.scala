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

import scala.concurrent.duration._

sealed trait EphemeralDuration {
  val duration: FiniteDuration

  import EphemeralDuration._
  lazy val display: (Long, TimeUnit) = apply(duration)

}

object EphemeralDuration {
  sealed trait TimeUnit
  case object Second  extends TimeUnit
  case object Minute  extends TimeUnit
  case object Hour    extends TimeUnit
  case object Day     extends TimeUnit
  case object Week    extends TimeUnit

  def apply(duration: Duration): (Long, TimeUnit) = {
    import java.util.concurrent.TimeUnit._

    def loop(duration: Duration): (Long, TimeUnit) = {

      val coarse = duration.toCoarsest

      def roundSeconds(divider: Long) =
        loop(FiniteDuration(math.round(coarse.length / divider.toDouble), SECONDS))

      def roundAndLoopOrThis(divider: Int, unit: TimeUnit) = {
        val nextUnit = unit match {
          case Second => MINUTES
          case Minute => HOURS
          case _      => DAYS
        }
        if (coarse.length >= divider)
          loop(FiniteDuration(math.round(coarse.length.toDouble / divider.toDouble), nextUnit))
        else
          (coarse.length, unit)
      }

      if (coarse == Duration.Zero)
        (0, Second)
      else
        coarse.unit match {
          case NANOSECONDS  => roundSeconds(1.second.toNanos)
          case MICROSECONDS => roundSeconds(1.second.toMicros)
          case MILLISECONDS => roundSeconds(1.second.toMillis)
          case SECONDS => roundAndLoopOrThis(60, Second)
          case MINUTES => roundAndLoopOrThis(60, Minute)
          case HOURS   => roundAndLoopOrThis(24, Hour)
          case DAYS =>
            if (coarse.length >= 7)
              (math.round(coarse.length.toDouble / 7d), Week)
            else
              (coarse.length, Day)
        }
    }

    loop(duration)
  }
}

case class ConvExpiry(duration: FiniteDuration) extends EphemeralDuration
case class MessageExpiry(duration: FiniteDuration) extends EphemeralDuration
