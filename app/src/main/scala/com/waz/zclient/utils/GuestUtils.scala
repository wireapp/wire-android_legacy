/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.utils

import android.content.Context
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils.getString
import org.threeten.bp.Instant
import com.waz.utils._

object GuestUtils {
  def timeRemainingString(expiration: Instant, now: Instant)(implicit ctx: Context): String = {
    val diff = now.until(expiration)

    def hoursString(hours: Long) =
      getString(R.string.guest_time_left_hours, hours.toString)
    def hoursAndMinutesString(hours: Long, minutes: Long) =
      getString(R.string.guest_time_left_hours_minutes, hours.toString, minutes.toString)
    def minutesString(minutes: Long) =
      getString(R.string.guest_time_left_minutes, minutes.toString)

    (diff.toHours, diff.toMinutes % 60) match {
      case (hoursLeft, _) if hoursLeft >= 2     => hoursString(hoursLeft + 1)
      case (1, minutesLeft) if minutesLeft > 30 => hoursString(2)
      case (1, _)                               => hoursAndMinutesString(1, 30)
      case (_, minutesLeft) if minutesLeft > 45 => hoursString(1)
      case (_, minutesLeft) if minutesLeft > 30 => minutesString(45)
      case (_, minutesLeft) if minutesLeft > 15 => minutesString(30)
      case _                                    => minutesString(15)
    }
  }
}
