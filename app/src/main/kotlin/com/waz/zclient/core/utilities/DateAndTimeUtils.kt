package com.waz.zclient.core.utilities

import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

//Utils class - do not instantiate
class DateAndTimeUtils private constructor() {

    companion object {

        private const val TWO_MINUTES = 2L
        private const val SIXTY_MINUTES = 60L

        fun getTimeStamp(time: String): String {
            val now = LocalDateTime.now()
            val localTime = LocalDateTime.ofInstant(Instant.parse(time), ZoneId.systemDefault())
            val isLastTwoMins = now.minusMinutes(TWO_MINUTES).isBefore(localTime)
            val isLastSixtyMinutes = now.minusMinutes(SIXTY_MINUTES).isBefore(localTime)

            return when {
                isLastTwoMins -> "Just now"
                isLastSixtyMinutes -> "${Duration.between(localTime, now).toMinutes().toInt()} minutes ago"
                else -> time
            }
        }
    }
}
