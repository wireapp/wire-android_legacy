package com.waz.zclient.core.utilities

import com.waz.zclient.UnitTest
import com.waz.zclient.core.utilities.DateAndTimeUtils.instantToString
import org.amshove.kluent.shouldEqual
import org.junit.Test
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

class DateAndTimeUtilsTest : UnitTest() {

    @Test
    fun `given the epoch as the instant and the date as the format, when converted to string, then return September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE, zoneId = zoneId) shouldEqual "2020-09-13"
    }

    @Test
    fun `given the epoch as the instant and the short date as the format, when converted to string, then return September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_SHORT, zoneId = zoneId) shouldEqual "20200913"
    }

    @Test
    fun `given the epoch as the instant and the date-time as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME, zoneId = zoneId) shouldEqual "2020-09-13 13:40:00"
    }

    @Test
    fun `given the epoch as the instant and the short date-time as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME_SHORT, zoneId = zoneId) shouldEqual "20200913134000"
    }

    @Test
    fun `given the epoch as the instant and the date-time with the timezone as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME_ZONE, zoneId = zoneId) shouldEqual "2020-09-13 13:40:00+0000"
    }

    @Test
    fun `given the epoch as the instant and the long date-time as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME_LONG, zoneId = zoneId) shouldEqual "2020-09-13 13:40:00.000"
    }

    @Test
    fun `given the epoch as the instant and the long date-time with the timezone as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME_ZONE_LONG, zoneId = zoneId) shouldEqual "2020-09-13 13:40:00.000+0000"
    }

    @Test
    fun `given the epoch as the instant and ISO-8601 as the format, when converted to string, then return 13h40, September 13, 2020, in the correct format`() {
        instantToString(instant, DatePattern.DATE_TIME_ISO8601, zoneId = zoneId) shouldEqual "2020-09-13T13:40:00.000Z"
    }

    companion object {
        private val zoneId = ZoneId.of("GMT")
        private val instant = ZonedDateTime.of(2020, 9, 13, 13, 40, 0, 0, zoneId).toInstant()
    }
}
