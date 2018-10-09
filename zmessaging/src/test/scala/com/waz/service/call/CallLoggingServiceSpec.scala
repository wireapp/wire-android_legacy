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

import com.waz.model.{ConvId, RemoteInstant, UserId}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.call.Avs.AvsClosedReason
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState._
import com.waz.service.messages.MessagesService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import org.threeten.bp.{Duration, Instant}
import com.waz.utils.RichInstant

import scala.concurrent.Future
import scala.concurrent.duration._

class CallLoggingServiceSpec extends AndroidFreeSpec {

  import Threading.Implicits.Background

  val selfUserId = UserId("self-user")

  val calling   = mock[CallingService]
  val messages  = mock[MessagesService]
  val push      = mock[PushService]

  val calls = Signal(Map.empty[ConvId, CallInfo]).disableAutowiring()

  scenario("Outgoing call is tracked through all state changes") {

    val convId = ConvId("conv")

    lazy val outgoingCall        = CallInfo(convId, selfUserId, isGroup = false, selfUserId, SelfCalling)
    lazy val joiningCall         = outgoingCall   .updateCallState(SelfJoining)
    lazy val establishedCall     = joiningCall    .updateCallState(SelfConnected)
    lazy val endedCall           = establishedCall.updateCallState(Ended)
    lazy val endedCallWithReason = endedCall      .updateCallState(Ended).copy(endReason = Some(AvsClosedReason.Normal))

    val trackingCalledHistory = Signal(Seq.empty[(CallState, Option[AvsClosedReason])])
    (tracking.trackCallState _).expects(selfUserId, *).repeat(4 to 4).onCall { (_, call) =>
      Future(trackingCalledHistory.mutate(_ :+ (call.state, call.endReason)))
    }

    (messages.addSuccessfulCallMessage _).expects(convId, selfUserId, RemoteInstant.apply(Instant.EPOCH + 35.seconds), 60.seconds).returning(Future.successful(None)) //return not important

    initService()

    calls.mutate(_ + (convId -> outgoingCall))

    await(trackingCalledHistory.filter {
      case Seq((SelfCalling, None)) => true
      case _ => false
    }.head)

    clock + 30.seconds //rings for 30 seconds
    calls.mutate(_ + (convId -> joiningCall))

    await(trackingCalledHistory.filter {
      case Seq((SelfCalling, None), (SelfJoining, None)) => true
      case _ => false
    }.head)

    clock + 5.seconds //joins for 5 seconds
    calls.mutate(_ + (convId -> establishedCall))

    await(trackingCalledHistory.filter {
      case Seq((SelfCalling, None), (SelfJoining, None), (SelfConnected, None)) => true
      case _ => false
    }.head)

    clock + 60.seconds //established for a minute
    calls.mutate(_ + (convId -> endedCall))

    awaitAllTasks
    await(trackingCalledHistory.filter {
      case Seq((SelfCalling, None), (SelfJoining, None), (SelfConnected, None)) => true
      case _ => false
    }.head)

    clock + 5.seconds //takes 5 seconds for the callback to trigger
    calls.mutate(_ + (convId -> endedCallWithReason))

    await(trackingCalledHistory.filter {
      case Seq((SelfCalling, None), (SelfJoining, None), (SelfConnected, None), (Ended, Some(AvsClosedReason.Normal))) => true
      case _ => false
    }.head)
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

    calls.mutate(_ + (convId1 -> incomingCall1))

    await(trackingCalledConv1.filter(_ == 1).head)

    calls.mutate(_ + (convId2 -> incomingCall2))

    await(trackingCalledConv2.filter(_ == 1).head)

    //User answers call 1
    calls.mutate(_ + (convId1 -> incomingCall1.updateCallState(SelfJoining)))

    await(trackingCalledConv1.filter(_ == 2).head)
  }

  def initService() = {

    (calling.calls _).expects().anyNumberOfTimes().returning(calls)
    (push.beDrift _).expects().anyNumberOfTimes().returning(Signal.const(Duration.ZERO))

    new CallLoggingService(selfUserId, calling, messages, push, tracking)
  }

}
