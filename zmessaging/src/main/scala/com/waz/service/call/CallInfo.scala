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
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.{ConvId, GenericMessage, LocalInstant, UserId}
import com.waz.service.call.Avs.AvsClosedReason.reasonString
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.Avs.{AvsClosedReason, VideoState}
import com.waz.service.call.CallInfo.CallState
import com.waz.service.call.CallInfo.CallState._
import com.waz.utils.events.{ClockSignal, Signal}
import org.threeten.bp.Duration
import org.threeten.bp.Duration.between

import scala.concurrent.duration._

case class CallInfo(convId:             ConvId,
                    account:            UserId,
                    isGroup:            Boolean,
                    caller:             UserId,
                    state:              CallState,
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
                    endReason:          Option[AvsClosedReason]           = None,
                    outstandingMsg:     Option[(GenericMessage, Pointer)] = None, //Any messages we were unable to send due to conv degradation
                    shouldRing:         Boolean                           = true) {

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
       | endReason:          ${endReason.map(reasonString)}
       | hasOutstandingMsg:  ${outstandingMsg.isDefined}
       | shouldRing:         $shouldRing
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
    case OtherCalling => startedAsVideoCall
    case _            => allVideoReceiveStates.exists(_._2 != Stopped)
  }

  /**
    * Collapses the join state into either OtherCalling or SelfCalling. Useful for some UI states that don't need
    * such fine-grained information
    */
  val stateCollapseJoin = (state, prevState) match {
    case (SelfJoining,   Some(OtherCalling)) => OtherCalling
    case (SelfJoining,   _)                  => SelfCalling //the _ should always be Some(SelfCalling) here
    case (s,             _)                  => s
  }

  def updateCallState(newState: CallState): CallInfo = {
    val changedState = newState != this.state

    val withState = copy(
      state     = newState,
      prevState = if (!changedState) this.prevState else Some(this.state)
    )

    newState match {
      case SelfJoining                   => withState.copy(joinedTime = Some(LocalInstant.Now))
      case SelfConnected if changedState => withState.copy(estabTime  = Some(LocalInstant.Now))
      case Terminating |
           Ended if Set(SelfConnected, SelfJoining).exists(prevState.contains) =>
        withState.copy(endTime = endTime.orElse(Some(LocalInstant.Now))) //we may call Terminating/Ended multiple times - always take the first
      case _                             => withState
    }
  }

  def updateVideoState(userId: UserId, videoState: VideoState): CallInfo = {

    val newCall: CallInfo =
      if (userId == account) this.copy(videoSendState = videoState)
      else this.copy(videoReceiveStates = this.videoReceiveStates + (userId -> videoState))

    verbose(s"updateVideoSendState: $userId, $videoState, newCall: $newCall")

    val wasVideoToggled = newCall.wasVideoToggled || (newCall.isVideoCall != this.isVideoCall)
    newCall.copy(wasVideoToggled = wasVideoToggled)
  }

}

object CallInfo {

  sealed trait CallState

  object CallState {

    case object SelfCalling   extends CallState //the self user is calling into a conversation.
    case object OtherCalling  extends CallState //another user is calling us
    case object SelfJoining   extends CallState //either we or the other use have joined the call, but there is no established audio yet
    case object SelfConnected extends CallState //the self user has an established video call
    case object Ongoing       extends CallState //there is a background call that the self user has ignored or dismissed, but can join (group calls or ignored incoming calls)
    case object Terminating   extends CallState //the call no longer has any audio, but may still be displayed in the UI
    case object Ended         extends CallState //this call is finished and cleaned up. It should not be shown in the UI

    val ActiveCallStates   = Set[CallState](SelfCalling, SelfJoining, SelfConnected, Terminating)
    val JoinableCallStates = Set[CallState](SelfCalling, OtherCalling, SelfJoining, SelfConnected, Ongoing)
    val FinishedStates = Set[CallState](Terminating, Ended)

    def isActive(st: CallState, shouldRing: Boolean): Boolean = ActiveCallStates(st) || (st == OtherCalling && shouldRing)
    def isJoinable(st: CallState): Boolean = JoinableCallStates(st)
    def isFinished(st: CallState): Boolean = FinishedStates(st)
  }
}
