/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.calling.controllers

import _root_.com.waz.zclient.utils.{DeprecationUtils, LayoutSpec}
import android.media.AudioManager
import android.os.Vibrator
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.VideoSendState.{DONT_SEND, SEND}
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.model._
import com.waz.service.call.Avs.VideoReceiveState
import com.waz.service.call.CallInfo.CallState._
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.{ButtonSignal, ClockSignal, EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.calling.views.CallControlButtonView.{ButtonColor, ButtonSettings}
import org.threeten.bp.Duration._
import org.threeten.bp.Instant._
import org.threeten.bp.{Duration, Instant}

class CurrentCallController(implicit inj: Injector, cxt: WireContext, ec: EventContext) extends Injectable { self =>

  val glob = inject[GlobalCallingController]
  import glob._

  import Threading.Implicits.Background

  val showOngoingControls = convDegraded.flatMap {
    case true => Signal(true)
    case false => callState.map {
      case OtherCalling => false
      case _ => true
    }
  }.orElse(Signal(true)) //ensure that controls are ALWAYS visible in case something goes wrong...

  val videoSendState = currentCallOpt.collect { case Some(c) => c.videoSendState }
    .disableAutowiring()


  val flowManager = zms map (_.flowmanager)

  val captureDevices = flowManager.flatMap(fm => Signal.future(fm.getVideoCaptureDevices))

  //TODO when I have a proper field for front camera, make sure it's always set as the first one
  val currentCaptureDeviceIndex = Signal(0)

  val currentCaptureDevice = captureDevices.zip(currentCaptureDeviceIndex).map {
    case (devices, devIndex) if devices.nonEmpty => Some(devices(devIndex % devices.size))
    case _ => None
  }

  (for {
    fm     <- flowManager
    conv   <- conversation
    device <- currentCaptureDevice
  } yield (fm, conv, device)) {
    case (fm, conv, Some(currentDevice)) => fm.setVideoCaptureDevice(conv.remoteId, currentDevice.id)
    case _ =>
  }

  val otherUser = Signal(groupCall, userStorage, convId).flatMap {
    case (isGroupCall, usersStorage, convId) if !isGroupCall =>
      usersStorage.optSignal(UserId(convId.str)) // one-to-one conversation has the same id as the other user, so we can access it directly
    case _ => Signal.const[Option[UserData]](None) //Need a none signal to help with further signals
  }

  val duration = {
    def timeSince(est: Option[Instant]) = ClockSignal(Duration.ofSeconds(1).asScala).map(_ => est.fold2(ZERO, between(_, now)))
    currentCall.map(_.estabTime).flatMap(timeSince)
  }

  val subtitleText: Signal[String] = convDegraded.flatMap {
    case true => Signal("")
    case false => (for {
      video <- videoCall
      state <- callState
      dur <- duration map { duration =>
        val seconds = ((duration.toMillis / 1000) % 60).toInt
        val minutes = ((duration.toMillis / 1000) / 60).toInt
        f"$minutes%02d:$seconds%02d"
      }
    } yield (video, state, dur)).map {
      case (true,  SelfCalling,  _)                    => cxt.getString(R.string.calling__header__outgoing_video_subtitle)
      case (false, SelfCalling,  _)                    => cxt.getString(R.string.calling__header__outgoing_subtitle)
      case (true,  OtherCalling, _)                    => cxt.getString(R.string.calling__header__incoming_subtitle__video)
      case (false, OtherCalling, _)                    => cxt.getString(R.string.calling__header__incoming_subtitle)
      case (_,     SelfJoining,  _)                    => cxt.getString(R.string.calling__header__joining)
      case (false, SelfConnected, duration)            => duration
      case _ => ""
    }
  }

  val videoReceiveState = currentCall.map(_.videoReceiveState)

  val cameraFailed = flowManager.flatMap(_.cameraFailedSig)

  val stateMessageText = Signal(callState, cameraFailed, videoReceiveState, conversationName).map { vs =>
      verbose(s"$vs")
      import VideoReceiveState._
      vs match {
        case (SelfCalling,   true, _,             _)             => Option(cxt.getString(R.string.calling__self_preview_unavailable_long))
        case (SelfJoining,   _,    _,             _)             => Option(cxt.getString(R.string.ongoing__connecting))
        case (SelfConnected, _,    BadConnection, _)             => Option(cxt.getString(R.string.ongoing__poor_connection_message))
        case (SelfConnected, _,    Stopped,       otherUserName) => Option(cxt.getString(R.string.ongoing__other_turned_off_video, otherUserName))
        case (SelfConnected, _,    Unknown,       otherUserName) => Option(cxt.getString(R.string.ongoing__other_unable_to_send_video, otherUserName))
        case _ => None
      }
  }

  val participantIdsToDisplay = currentCallOpt.map(_.fold(Vector.empty[UserId])(_.others.toVector))

  val flowId = for {
    zms <- zms
    convId <- convId
    conv <- zms.convsStorage.signal(convId)
    rConvId = conv.remoteId
    userData <- otherUser
  } yield (rConvId, userData.map(_.id))

  def setVideoPreview(view: Option[VideoPreview]): Unit = {
    flowManager.on(Threading.Ui) { fm =>
      verbose(s"Setting VideoPreview on Flowmanager, view: $view")
      fm.setVideoPreview(view.orNull)
    }
  }

  def setVideoView(view: Option[VideoRenderer]): Unit = {
    (for {
      fm <- flowManager
      (rConvId, userId) <- flowId
    } yield (fm, rConvId, userId)).on(Threading.Ui) {
      case (fm, rConvId, userId) =>
        verbose(s"Setting ViewRenderer on Flowmanager, rConvId: $rConvId, userId: $userId, view: $view")
        view.foreach(fm.setVideoView(rConvId, userId, _))
    }
  }

  def dismissCall(): Unit = {
    verbose(s"dismiss call")
    callingServiceAndCurrentConvId.currentValue.foreach {
      case (cs, id) => cs.endCall(id)
    }
  }

  def leaveCall(): Unit = {
    verbose(s"leave call")
    callingServiceAndCurrentConvId.currentValue.foreach {
      case (cs, id) => cs.endCall(id)
    }
  }

  def continueDegradedCall(): Unit = callingServiceAndCurrentConvId.head.map {
    case (cs, _) => cs.continueDegradedCall()
  }

  def vibrate(): Unit = {
    import com.waz.zclient.utils.ContextUtils._
    val audioManager = Option(inject[AudioManager])
    val vibrator = Option(inject[Vibrator])

    val disableRepeat = -1
    (audioManager, vibrator) match {
      case (Some(am), Some(vib)) if am.getRingerMode != AudioManager.RINGER_MODE_SILENT =>
        DeprecationUtils.vibrate(vib, getIntArray(R.array.call_control_enter).map(_.toLong), disableRepeat)
      case _ =>
    }
  }

  def toggleMuted(): Unit =
    callingServiceAndCurrentConvId.currentValue.foreach(_._1.setCallMuted(!muted.currentValue.getOrElse(false)))

  def toggleVideo(): Unit = {
    val state = videoSendState.currentValue.getOrElse(DONT_SEND)
    callingServiceAndCurrentConvId.currentValue.foreach {
      case (s, cId) => s.setVideoSendActive(cId, if(state == SEND) false else true)
    }
  }
  val cbrEnabled: Signal[String] = callingService.flatMap(_.currentCall.map(_.fold(false)(_.isCbrEnabled))).map {
    case false => ""
    case true => cxt.getString(R.string.audio_message__constant_bit_rate)
  }

  val speakerButton = ButtonSignal(zms.map(_.mediamanager), zms.flatMap(_.mediamanager.isSpeakerOn)) {
    case (mm, isSpeakerSet) => mm.setSpeaker(!isSpeakerSet)
  }

  val leftButtonSettings = convDegraded.flatMap {
    case true =>
      outgoingCall.map { outgoing =>
        ButtonSettings(R.string.glyph__close, R.string.confirmation_menu__cancel, () => if (outgoing) leaveCall() else dismissCall())
      }
    case false => Signal(ButtonSettings(R.string.glyph__microphone_off, R.string.incoming__controls__ongoing__mute, () => toggleMuted()))
  }

  val middleButtonSettings = convDegraded.flatMap {
    case true  =>
      outgoingCall.map { outgoing =>
        val text = if (outgoing) R.string.conversation__action__call else R.string.incoming__controls__incoming__accept
        ButtonSettings(R.string.glyph__call, text, () => continueDegradedCall(), ButtonColor.Green)
      }
    case false => Signal(ButtonSettings(R.string.glyph__end_call, R.string.incoming__controls__ongoing__hangup, () => leaveCall(), ButtonColor.Red))
  }

  val rightButtonSettings = videoCall.map {
    case true  => ButtonSettings(R.string.glyph__video,        R.string.incoming__controls__ongoing__video,   () => toggleVideo())
    case false => ButtonSettings(R.string.glyph__speaker_loud, R.string.incoming__controls__ongoing__speaker, () => speakerButton.press())
  }

  val isTablet = Signal(!LayoutSpec.isPhone(cxt))

  val rightButtonShown = convDegraded.flatMap {
    case true  => Signal(false)
    case false => Signal(videoCall, activeCallEstablished, captureDevices, isTablet) map {
      case (true, false, _, _) => false
      case (true, true, captureDevices, _) => captureDevices.size >= 0
      case (false, _, _, isTablet) => !isTablet //Tablets don't have ear-pieces, so you can't switch between speakers
      case _ => false
    }
  }
}
