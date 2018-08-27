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

import com.waz.model.{ConvId, UserId}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading
import com.waz.utils.events.Signal

import scala.concurrent.Future

class CallLoggingServiceSpec extends AndroidFreeSpec {

  import Threading.Implicits.Background

  val selfUserId = UserId("self-user")

  val calling   = mock[CallingService]
  val messages  = mock[MessagesService]
  val push      = mock[PushService]

  val calls = Signal(Map.empty[ConvId, CallInfo]).disableAutowiring()

  scenario("Outgoing call is tracked") {

    val convId = ConvId("conv")
    val outgoingCall = CallInfo(convId, selfUserId, isGroup = false, selfUserId, SelfCalling)

    val trackingCalled = Signal(false)
    (tracking.trackCallState _).expects(selfUserId, outgoingCall).onCall { (_, _) =>
      Future(trackingCalled ! true)
    }

    initService()

    calls.mutate {
      _ + (convId -> outgoingCall)
    }

    await(trackingCalled.filter(identity).head)
  }

  scenario("Two incoming calls in different conversations are tracked") {

    val convId1 = ConvId("conv1")
    val convId2 = ConvId("conv2")

    val incomingCall1 = CallInfo(convId1, selfUserId, isGroup = false, UserId("other-user1"), OtherCalling)
    val incomingCall2 = CallInfo(convId2, selfUserId, isGroup = false, UserId("other-user2"), OtherCalling)

    val trackingCalledConv1 = Signal(0)
    val trackingCalledConv2 = Signal(0)
    (tracking.trackCallState _)
      .expects(selfUserId, *)
      .repeat(3 to 3)
      .onCall { (_, call) =>
        Future {
          call.convId match {
            case `convId1` => trackingCalledConv1.mutate(_ + 1)
            case `convId2` => trackingCalledConv2.mutate(_ + 1)
          }
        }
      }

    initService()

    calls.mutate {
      _ + (convId1 -> incomingCall1)
    }

    await(trackingCalledConv1.filter(_ == 1).head)

    calls.mutate {
      _ + (convId2 -> incomingCall2)
    }

    await(trackingCalledConv2.filter(_ == 1).head)

    //User answers call 1
    calls.mutate {
      _ + (convId1 -> incomingCall1.updateCallState(SelfJoining))
    }

    await(trackingCalledConv1.filter(_ == 2).head)

  }


  def initService() = {

    (calling.calls _).expects().anyNumberOfTimes().returning(calls)

    new CallLoggingService(selfUserId, calling, messages, push, tracking)
  }

}
