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
package com.waz.zclient.calling.views

import android.Manifest.permission.{CAMERA, RECORD_AUDIO}
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.permissions.PermissionsService
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.CallInfo.CallState.{SelfCalling, SelfConnected, SelfJoining}
import com.waz.service.call.{CallInfo, CallingService}
import com.waz.utils.events.{EventStream, Signal, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.CallControlButtonView.ButtonColor
import com.waz.zclient.paintcode._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

import scala.async.Async._
import scala.concurrent.Future

class ControlsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends GridLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import com.waz.threading.Threading.Implicits.Ui

  inflate(R.layout.calling__controls__grid, this)
  setColumnCount(3)
  setRowCount(2)

  private lazy val controller  = inject[CallController]
  private lazy val permissions = inject[PermissionsService]

  val onButtonClick: SourceStream[Unit] = EventStream[Unit]

  controller.callStateOpt.onUi { state =>
    verbose(s"callStateOpt: $state")
  }

  private val isVideoBeingSent = controller.videoSendState.map(p => !Set(Stopped, NoCameraPermission).contains(p))

  // first row
  returning(findById[CallControlButtonView](R.id.mute_call)) { button =>
    button.set(WireStyleKit.drawMute, R.string.incoming__controls__ongoing__mute, mute)
    button.setEnabled(true)
    controller.isMuted.onUi(button.setActivated)
  }

  returning(findById[CallControlButtonView](R.id.video_call)) { button =>
    button.set(WireStyleKit.drawCamera, R.string.incoming__controls__ongoing__video, video)

    isVideoBeingSent.onUi(button.setActivated)

    (for {
      zms            <- controller.callingZms
      conv           <- controller.conversation
      isGroup        <- zms.conversations.groupConversation(conv.id)
      isTeam         =  zms.teamId.isDefined
      established    <- controller.isCallEstablished
      showVideo      <- controller.isVideoCall
      members        <- controller.conversationMembers.map(_.size)
    } yield members <= CallingService.VideoCallMaxMembers && ((established && (isTeam || !isGroup)) || showVideo)).onUi(button.setEnabled)
  }

  returning(findById[CallControlButtonView](R.id.speaker_flip_call)) { button =>
    button.setEnabled(true)
    isVideoBeingSent.onUi {
      case true =>
        button.set(WireStyleKit.drawFlip, R.string.incoming__controls__ongoing__flip, flip)
      case false =>
        button.set(WireStyleKit.drawSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
    }
    Signal(controller.speakerButton.buttonState, isVideoBeingSent).onUi {
      case (buttonState, false) => button.setActivated(buttonState)
      case _                    => button.setActivated(false)
    }
  }

  // second row
  returning(findById[CallControlButtonView](R.id.reject_call)) { button =>
    button.setEnabled(true)
    button.set(WireStyleKit.drawHangUpCall, R.string.empty_string, leave, Some(ButtonColor.Red))
    controller.isCallIncoming.onUi(button.setVisible)
  }

  returning(findById[CallControlButtonView](R.id.end_call)) { button =>
    button.set(WireStyleKit.drawHangUpCall, R.string.empty_string, leave, Some(ButtonColor.Red))
    controller.callStateOpt.map(_.exists(state => Set[CallInfo.CallState](SelfJoining, SelfCalling, SelfConnected).contains(state))).onUi { visible =>
      button.setVisibility(if(visible) View.VISIBLE else View.INVISIBLE)
      button.setEnabled(visible)
    }
  }

  returning(findById[CallControlButtonView](R.id.accept_call)) { button =>
    button.set(WireStyleKit.drawAcceptCall, R.string.empty_string, accept, Some(ButtonColor.Green))
    button.setEnabled(true)
    controller.isCallIncoming.onUi(button.setVisible)
  }

  private def accept(): Future[Unit] = async {
    onButtonClick ! {}
    val sendingVideo  = await(controller.videoSendState.head) == Started
    val perms         = await(permissions.requestPermissions(if (sendingVideo) Set(CAMERA, RECORD_AUDIO) else Set(RECORD_AUDIO)))
    val cameraGranted = perms.exists(p => p.key.equals(CAMERA) && p.granted)
    val audioGranted  = perms.exists(p => p.key.equals(RECORD_AUDIO) && p.granted)
    val callingConvId = await(controller.callConvId.head)
    val callingZms    = await(controller.callingZms.head)

    if (audioGranted)
      callingZms.calling.startCall(callingConvId, await(controller.isVideoCall.head))
    else
      showPermissionsErrorDialog(R.string.calling__cannot_start__title,
        R.string.calling__cannot_start__no_permission__message,
        R.string.calling__cannot_start__cancel__message
        ).flatMap(_ => callingZms.calling.endCall(callingConvId))
  }

  private def leave(): Unit = {
    onButtonClick ! {}
    controller.leaveCall()
  }

  private def flip(): Unit = {
    onButtonClick ! {}
    controller.currentCaptureDeviceIndex.mutate(_ + 1)
  }

  private def speaker(): Unit = {
    onButtonClick ! {}
    controller.speakerButton.press()
  }

  private def video(): Future[Unit] = async {
    onButtonClick ! {}
    val hasCameraPermissions = await(permissions.requestAllPermissions(Set(CAMERA)))

    if (!hasCameraPermissions)
      showPermissionsErrorDialog(
        R.string.calling__cannot_start__title,
        R.string.calling__cannot_start__no_camera_permission__message
      )
    else controller.toggleVideo()
  }

  private def mute(): Unit = {
    onButtonClick ! {}
    controller.toggleMuted()
  }
}
