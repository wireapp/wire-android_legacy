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
package com.waz.service.call

import com.waz.model.{ConvId, LocalInstant, UserId}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.call.CallInfo.CallState.{Ended, SelfConnected}
import com.waz.specs.AndroidFreeSpec

import scala.concurrent.duration._

class CallInfoSpec extends AndroidFreeSpec {

  scenario("Test duration advances once every second") {
    val call = callInfo()

    call.durationFormatted(println)

    clock + 1.seconds
    Thread.sleep(1050)
    result(call.durationFormatted.head) shouldEqual "00:01"

    clock + 1.seconds
    Thread.sleep(1050)
    result(call.durationFormatted.head) shouldEqual "00:02"

  }

  scenario("Test call duration formatted after an hour") {
    val call = callInfo()
    clock + 60.minutes
    clock + 35.seconds
    result(call.durationFormatted.head) shouldEqual "60:35"
  }

  scenario("Updating state with the same value twice should not override previous state") {
    callInfo()
      .updateCallState(Ended)
      .updateCallState(Ended).prevState shouldEqual Some(SelfConnected)
  }

  scenario("Updating established calls with the same state shouldn't restart established time") {
    val estTime = clock.instant()
    val call = callInfo().updateCallState(SelfConnected)
    clock + 1.seconds
    call.updateCallState(SelfConnected).estabTime shouldEqual Some(LocalInstant(estTime))
  }

  def callInfo() = CallInfo(
    ConvId("conversation"),
    account1Id,
    isGroup = false,
    UserId("callerId"),
    CallInfo.CallState.SelfConnected,
    estabTime = Some(LocalInstant.Now(clock))
  )

}
