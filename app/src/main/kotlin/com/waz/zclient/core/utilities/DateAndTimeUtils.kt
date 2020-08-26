package com.waz.zclient.core.utilities

import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import java.text.SimpleDateFormat
import java.util.Locale

enum class DatePattern(val pattern: String) {
    DATE("yyyy-MM-dd"),
    DATE_SHORT("yyyyMMdd"),
    DATE_TIME("yyyy-MM-dd HH:mm:ss"),
    DATE_TIME_SHORT("yyyyMMddHHmmss"),
    DATE_TIME_ZONE("yyyy-MM-dd HH:mm:ss'Z'"),
    DATE_TIME_LONG("yyyy-MM-dd HH:mm:ss.SSS"),
    DATE_TIME_ZONE_LONG("yyyy-MM-dd HH:mm:ss.SSS'Z'")
}

object DateAndTimeUtils {
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

    fun instantToString(
        instant: Instant = Instant.now(),
        pattern: DatePattern = DatePattern.DATE_TIME,
        locale: Locale = Locale.getDefault()
    ): String =
        SimpleDateFormat(pattern.pattern, locale).format(instant.toEpochMilli())
}
