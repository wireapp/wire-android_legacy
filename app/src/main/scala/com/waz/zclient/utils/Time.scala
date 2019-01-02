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
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.log.ZLog2._
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils.{getQuantityString, getString}
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.{DateTimeException, _}

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
      getQuantityString(R.plurals.timestamp__x_minutes_ago_1, minutes, minutes.toString)
  }

  sealed trait DateTimeStamp extends TimeStamp {

    val isSameDay: Boolean = this match {
      case _: SameDayTimeStamp => true
      case _ => false
    }

    protected def timePattern(implicit context: Context): String =
      getString(if (DateFormat.is24HourFormat(context)) R.string.timestamp_pattern__24h_format else R.string.timestamp_pattern__12h_format)
  }

  object DateTimeStamp {
    lazy val defaultDateFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    lazy val defaultTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    def format(pattern: String, localTime: LocalDateTime, formatter: DateTimeFormatter): String =
      try {
        DateTimeFormatter.ofPattern(pattern).format(localTime)
      } catch {
        case ex: IllegalArgumentException =>
          error(l"Invalid pattern ${showString(pattern)}", ex)
          formatter.format(localTime)
        case ex: DateTimeException =>
          error(l"Wrong pattern ${showString(pattern)} for local date time ${showString(localTime.toString)}", ex)
          formatter.format(localTime)
      }

    def apply(time: Instant, showWeekday: Boolean = true, now: LocalDateTime = LocalDateTime.now()): DateTimeStamp = {
      val localTime = LocalDateTime.ofInstant(time, ZoneId.systemDefault())
      val isSameDay = now.toLocalDate.atStartOfDay.isBefore(localTime)

      if (isSameDay) SameDayTimeStamp(localTime)
      else FullTimeStamp(localTime, showWeekday)
    }
  }

  import DateTimeStamp._

  case class SameDayTimeStamp(localTime: LocalDateTime) extends DateTimeStamp {
    override def string(implicit context: Context): String = format(timePattern, localTime, defaultTimeFormatter)
  }

  object SameDayTimeStamp {
    def apply(time: Instant): SameDayTimeStamp =
      new SameDayTimeStamp(LocalDateTime.ofInstant(time, ZoneId.systemDefault()))
  }

  case class FullTimeStamp(localTime: LocalDateTime, showWeekday: Boolean) extends DateTimeStamp {
    override def string(implicit context: Context): String = {
      val datePatternRes = (LocalDateTime.now().getYear == localTime.getYear, showWeekday) match {
        case (true, true)   => R.string.timestamp_pattern__date_and_time__no_year
        case (true, false)  => R.string.timestamp_pattern__date_and_time__no_year_no_weekday
        case (false, true)  => R.string.timestamp_pattern__date_and_time__with_year
        case (false, false) => R.string.timestamp_pattern__date_and_time__with_year_no_weekday
      }

      format(getString(datePatternRes, timePattern), localTime, defaultDateFormatter)
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
