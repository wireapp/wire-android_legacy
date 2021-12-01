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
package com.waz.repository

import com.waz.model.{FCMNotification, Uid}
import com.waz.specs.AndroidFreeSpec
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit._

class FCMNotificationsRepositorySpec extends AndroidFreeSpec {

  import FCMNotification._
  import com.waz.repository.FCMNotificationsRepository._

  scenario("Oldest row is removed first when trimming") {
    val time1 = Instant.now
    val time3 = Instant.now.plus(10, HOURS)
    val time2 = Instant.now.plus(5, HOURS)
    val times = Vector(time1, time2, time3)
    val input = times.map(t => FCMNotification(Uid("test"), StartedPipeline, t))
    val expectedOutput = Vector(FCMNotification(Uid("test"), StartedPipeline, time1))

    getOldestExcessRows(input, 2) shouldEqual expectedOutput
  }

  scenario("Oldest rows are ordered temporally when trimming") {
    val time1 = Instant.now
    val time3 = Instant.now.plus(10, HOURS)
    val time2 = Instant.now.plus(5, HOURS)
    val times = Vector(time1, time2, time3)
    val input = times.map(t => FCMNotification(Uid("test"), StartedPipeline, t))
    val expectedOutput = Vector(
      FCMNotification(Uid("test"), StartedPipeline, time1),
      FCMNotification(Uid("test"), StartedPipeline, time2))

    getOldestExcessRows(input, 1) shouldEqual expectedOutput
  }
}
