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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.log.LogSE._
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, GenericMessage, LocalInstant, UserId}
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.Avs.{AvsClosedReason, VideoState}
import com.waz.service.call.CallInfo.{ActiveSpeaker, CallState, OutstandingMessage, Participant, QOutstandingMessage}
import com.waz.service.call.CallInfo.CallState._
import com.waz.sync.otr.OtrSyncHandler.{QTargetRecipients, TargetRecipients}
import com.waz.utils.returning
import com.wire.signals.Signal
import com.wire.signals.ext.ClockSignal
import org.threeten.bp.Duration
import org.threeten.bp.Duration.between

import scala.concurrent.duration._

case class CallInfo(convId:             ConvId,
                    selfParticipant:    Participant,
                    isGroup:            Boolean,
                    caller:             UserId,
                    state:              CallState,
                    isConferenceCall:   Boolean                         = false,
                    prevState:          Option[CallState]               = None,
                    allParticipants:    Set[Participant]                = Set.empty,
                    maxParticipants:    Int                             = 0, //maintains the largest number of users that were ever in the call (for tracking)
                    muted:              Boolean                         = false,
                    isCbrEnabled:       Option[Boolean]                 = None,
                    startedAsVideoCall: Boolean                         = false,
                    videoSendState:     VideoState                      = VideoState.Stopped,
                    videoReceiveStates: Map[Participant, VideoState]    = Map.empty,
                    wasVideoToggled:    Boolean                         = false, //for tracking
                    wasScreenShareUsed: Boolean                         = false, //for tracking
                    screenShareStart:   Map[Participant, LocalInstant]  = Map.empty, //for tracking
                    screenShareEnded:   Option[(String, Long)]          = None, //for tracking
                    startTime:          LocalInstant                    = LocalInstant.Now, //the time we start/receive a call - always the time at which the call info object was created
                    joinedTime:         Option[LocalInstant]            = None, //the time the call was joined, if any
                    estabTime:          Option[LocalInstant]            = None, //the time that a joined call was established, if any
                    endTime:            Option[LocalInstant]            = None,
                    endReason:          Option[AvsClosedReason]         = None,
                    outstandingMsg:     Option[OutstandingMessage]      = None, //Any messages we were unable to send due to conv degradation
                    qOutstandingMsg:    Option[QOutstandingMessage]     = None,
                    shouldRing:         Boolean                         = true,
                    activeSpeakers:     Set[ActiveSpeaker]              = Set.empty
                   ) extends DerivedLogTag {

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

  val allVideoReceiveStates = videoReceiveStates + (selfParticipant -> videoSendState)

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

  def canOthersDialogue: Boolean = allParticipants.size > 1

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

  def updateVideoState(participant: Participant, videoState: VideoState): CallInfo = {

    import com.waz.utils.RichWireInstant
    def updateScreenShareTracking(i: CallInfo): CallInfo = {
      if (i.screenShareStart.contains(participant) && videoState != ScreenShare) {
        val duration = i.screenShareStart(participant).until(LocalInstant.Now).getSeconds
        val direction = if(participant == selfParticipant) "outgoing" else "incoming"
        i.copy(screenShareStart = i.screenShareStart - participant, screenShareEnded = Some(direction, duration))
      } else if(videoState == ScreenShare && !i.screenShareStart.contains(participant)) {
        i.copy(screenShareStart = i.screenShareStart + (participant -> LocalInstant.Now))
      } else i
    }

    def updateVideoFields(info: CallInfo): CallInfo =
      if (participant == selfParticipant) info.copy(videoSendState = videoState)
      else info.copy(videoReceiveStates = info.videoReceiveStates + (participant -> videoState))

    val updatedCallInfo: CallInfo = updateScreenShareTracking(updateVideoFields(this))

    val wasVideoToggled = updatedCallInfo.wasVideoToggled || (updatedCallInfo.isVideoCall != this.isVideoCall)
    val wasScreenShareUsed = updatedCallInfo.videoReceiveStates.values.count(_ == VideoState.ScreenShare) > 0

    returning(updatedCallInfo.copy(wasVideoToggled = wasVideoToggled, wasScreenShareUsed = wasScreenShareUsed)){ newCall =>
      verbose(l"updateVideoSendState: $participant, $videoState, newCall: $newCall")
    }
  }

}

object CallInfo {

  case class ActiveSpeaker(userId: UserId, clientId: ClientId, longTermAudioLevel: Int, instantAudioLevel: Int)

  case class Participant(userId: UserId, clientId: ClientId, muted: Boolean = false)

  case class OutstandingMessage(message: GenericMessage, recipients: TargetRecipients, context: Pointer)

  case class QOutstandingMessage(message: GenericMessage, recipients: QTargetRecipients, context: Pointer)

  sealed trait CallState extends SafeToLog

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
