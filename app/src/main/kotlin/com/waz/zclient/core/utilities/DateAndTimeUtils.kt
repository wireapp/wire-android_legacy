package com.waz.zclient.core.utilities

import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Instant
import org.threeten.bp.Duration
import org.threeten.bp.ZonedDateTime
import java.util.Locale

enum class DatePattern(val pattern: String) {
    DATE("yyyy-MM-dd"),
    DATE_SHORT("yyyyMMdd"),
    DATE_TIME("yyyy-MM-dd HH:mm:ss"),
    DATE_TIME_SHORT("yyyyMMddHHmmss"),
    DATE_TIME_ZONE("yyyy-MM-dd HH:mm:ssZ"),
    DATE_TIME_LONG("yyyy-MM-dd HH:mm:ss.SSS"),
    DATE_TIME_ZONE_LONG("yyyy-MM-dd HH:mm:ss.SSSZ"),
    DATE_TIME_ISO8601("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
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
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String =
        ZonedDateTime.ofInstant(instant, zoneId).format(DateTimeFormatter.ofPattern(pattern.pattern, locale))
}
