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
import android.text.format.DateFormat
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils.{getQuantityString, getString}
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.{Duration, Instant, LocalDateTime, ZoneId}

object Time {

  sealed trait TimeStamp {
    def string(implicit context: Context): String
  }

  case object JustNow extends TimeStamp {
    override def string(implicit context: Context): String =
      getString(R.string.timestamp__just_now)
  }

  case class MinutesAgo(minutes: Int) extends TimeStamp {
    override def string(implicit context: Context): String =
      getQuantityString(R.plurals.timestamp__x_minutes_ago, minutes, minutes.toString)
  }

  sealed trait DateTimeStamp extends TimeStamp {

    val isSameDay: Boolean = this match {
      case _: SameDayTimeStamp => true
      case _ => false
    }

    protected def timePattern(implicit context: Context): String =
      ResString(if (DateFormat.is24HourFormat(context)) R.string.timestamp_pattern__24h_format else R.string.timestamp_pattern__12h_format).resolve
  }

  object DateTimeStamp {
    def apply(time: Instant, showWeekday: Boolean = true, now: LocalDateTime = LocalDateTime.now()): DateTimeStamp = {
      val localTime = LocalDateTime.ofInstant(time, ZoneId.systemDefault())
      val isSameDay = now.toLocalDate.atStartOfDay.isBefore(localTime)

      if (isSameDay) SameDayTimeStamp(localTime)
      else FullTimeStamp(localTime, showWeekday)
    }
  }

  case class SameDayTimeStamp(localTime: LocalDateTime) extends DateTimeStamp {
    override def string(implicit context: Context): String =
      DateTimeFormatter.ofPattern(timePattern).format(localTime)
  }

  object SameDayTimeStamp {
    def apply(time: Instant): SameDayTimeStamp =
      new SameDayTimeStamp(LocalDateTime.ofInstant(time, ZoneId.systemDefault()))
  }

  case class FullTimeStamp(localTime: LocalDateTime, showWeekday: Boolean) extends DateTimeStamp {
    override def string(implicit context: Context): String = {

      val isThisYear = LocalDateTime.now().getYear == localTime.getYear

      val datePattern =
        if (isThisYear)
          if (showWeekday) getString(R.string.timestamp_pattern__date_and_time__no_year, timePattern)
          else getString(R.string.timestamp_pattern__date_and_time__no_year_no_weekday, timePattern)
        else if (showWeekday) getString(R.string.timestamp_pattern__date_and_time__with_year, timePattern)
        else getString(R.string.timestamp_pattern__date_and_time__with_year_no_weekday, timePattern)

      DateTimeFormatter.ofPattern(datePattern).format(localTime)
    }
  }

  object TimeStamp {

    def apply(time: Instant, showWeekday: Boolean = true): TimeStamp = {
      val now = LocalDateTime.now()
      val localTime = LocalDateTime.ofInstant(time, ZoneId.systemDefault())
      val isLastTwoMins   = now.minusMinutes(2).isBefore(localTime)
      val isLastSixtyMins = now.minusMinutes(60).isBefore(localTime)

      if (isLastTwoMins)
        JustNow
      else if (isLastSixtyMins)
        MinutesAgo(Duration.between(localTime, now).toMinutes.toInt)
      else
        DateTimeStamp(time, showWeekday, now)
    }
  }

}
