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

import com.sun.jna.Pointer
import com.waz.ZLog
import com.waz.model.{ConvId, GenericMessage, LocalInstant, UserId}
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState.{OtherCalling, SelfCalling, SelfConnected, SelfJoining}
import com.waz.utils.events.{ClockSignal, Signal}
import org.threeten.bp.Duration.between
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._

case class CallInfo(convId:             ConvId,
                    account:            UserId,
                    isGroup:            Boolean,
                    caller:             UserId,
                    state:              Option[CallState]                 = None,
                    prevState:          Option[CallState]                 = None,
                    others:             Map[UserId, Option[LocalInstant]] = Map.empty,
                    maxParticipants:    Int                               = 0, //maintains the largest number of users that were ever in the call (for tracking)
                    muted:              Boolean                           = false,
                    isCbrEnabled:       Boolean                           = false,
                    startedAsVideoCall: Boolean                           = false,
                    videoSendState:     VideoState                        = VideoState.Stopped,
                    videoReceiveStates: Map[UserId, VideoState]           = Map.empty,
                    wasVideoToggled:    Boolean                           = false, //for tracking
                    startTime:          LocalInstant                      = LocalInstant.Now, //the time we start/receive a call - always the time at which the call info object was created
                    joinedTime:         Option[LocalInstant]              = None, //the time the call was joined, if any
                    estabTime:          Option[LocalInstant]              = None, //the time that a joined call was established, if any
                    endTime:            Option[LocalInstant]              = None,
                    outstandingMsg:     Option[(GenericMessage, Pointer)] = None) { //Any messages we were unable to send due to conv degradation

  override def toString: String =
    s"""
       |CallInfo:
       | convId:             $convId
       | account:            $account
       | isGroup:            $isGroup
       | caller:             $caller
       | state:              $state
       | prevState:          $prevState
       | others:             $others
       | maxParticipants:    $maxParticipants
       | muted:              $muted
       | isCbrEnabled:       $isCbrEnabled
       | startedAsVideoCall: $startedAsVideoCall
       | videoSendState:     $videoSendState
       | videoReceiveStates: $videoReceiveStates
       | wasVideoToggled:    $wasVideoToggled
       | startTime:          $startTime
       | joinedTime:         $joinedTime
       | estabTime:          $estabTime
       | endTime:            $endTime
       | hasOutstandingMsg:  ${outstandingMsg.isDefined}
    """.stripMargin

  val duration = estabTime match {
    case Some(est) => ClockSignal(1.second).map(_ => Option(between(est.instant, LocalInstant.Now.instant)))
    case None      => Signal.const(Option.empty[Duration])
  }

  val durationFormatted = duration.map {
    case Some(d) =>
      val seconds = ((d.toMillis / 1000) % 60).toInt
      val minutes = ((d.toMillis / 1000) / 60).toInt
      f"$minutes%02d:$seconds%02d"
    case None => ""
  }

  val allVideoReceiveStates = videoReceiveStates + (account -> videoSendState)

  val isVideoCall = state match {
    case Some(OtherCalling) => startedAsVideoCall
    case _                  => allVideoReceiveStates.exists(_._2 != Stopped)
  }

  val stateCollapseJoin = (state, prevState) match {
    case (Some(OtherCalling),  _)                  => Some(OtherCalling)
    case (Some(SelfCalling),   _)                  => Some(SelfCalling)
    case (Some(SelfConnected), _)                  => Some(SelfConnected)
    case (Some(SelfJoining),   Some(OtherCalling)) => Some(OtherCalling)
    case (Some(SelfJoining),   Some(SelfCalling))  => Some(SelfCalling)
    case _ => None
  }

  def updateCallState(callState: CallState): CallInfo = {
    val withState = copy(state = Some(callState), prevState = this.state)
    callState match {
      case SelfJoining => withState.copy(joinedTime = Some(LocalInstant.Now))
      case SelfConnected => withState.copy(estabTime = Some(LocalInstant.Now))
      case _ => withState
    }
  }

  def updateVideoState(userId: UserId, videoState: VideoState): CallInfo = {

    val newCall: CallInfo =
      if (userId == account) this.copy(videoSendState = videoState)
      else this.copy(videoReceiveStates = this.videoReceiveStates + (userId -> videoState))

    ZLog.verbose(s"updateVideoSendState: $userId, $videoState, newCall: $newCall")("CallInfo")

    val wasVideoToggled = newCall.wasVideoToggled || (newCall.isVideoCall != this.isVideoCall)
    newCall.copy(wasVideoToggled = wasVideoToggled)
  }

}

object CallInfo {

  sealed trait CallState

  object CallState {

    case object SelfCalling    extends CallState
    case object OtherCalling   extends CallState
    case object SelfJoining    extends CallState
    case object SelfConnected  extends CallState
    case object Ongoing        extends CallState
  }
}
