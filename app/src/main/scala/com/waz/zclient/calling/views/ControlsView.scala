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
import android.content.res.Resources
import android.graphics.{Bitmap, Canvas, Paint, PorterDuff, PorterDuffColorFilter, RectF}
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.permissions.PermissionsService
import com.waz.service.call.Avs.VideoState._
import com.waz.service.call.CallInfo.CallState.{SelfCalling, SelfConnected, SelfJoining}
import com.waz.service.call.CallInfo
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.CallControlButtonView.ButtonColor
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.log.LogUI._
import com.waz.zclient.paintcode._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{ContextUtils, RichView}
import com.waz.zclient.{BuildConfig, R, ViewHelper, WireApplication}
import com.wire.signals.{EventStream, Signal, SourceStream}

import scala.async.Async._
import scala.collection.immutable.ListSet
import scala.concurrent.Future

class ControlsView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends GridLayout(context, attrs, defStyleAttr) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import com.waz.threading.Threading.Implicits.Ui

  inflate(R.layout.calling__controls__grid, this)
  setColumnCount(3)
  setRowCount(2)

  private lazy val controller  = inject[CallController]
  private lazy val permissions = inject[PermissionsService]
  private val themeController  = inject[ThemeController]
  private lazy val lightTheme = returning (WireApplication.APP_INSTANCE.getResources.newTheme){
    _.applyStyle(R.style.Theme_Light, true)
  }
  private lazy val darkTheme = returning (WireApplication.APP_INSTANCE.getResources.newTheme){
    _.applyStyle(R.style.Theme_Dark, true)
  }

  val onButtonClick: SourceStream[Unit] = EventStream[Unit]

  controller.callStateOpt.onUi { state =>
    verbose(l"callStateOpt: $state")
  }

  private val isVideoBeingSent = controller.videoSendState.map(p => !Set(Stopped, NoCameraPermission).contains(p))

  // first row
  returning(findById[CallControlButtonView](R.id.mute_call)) { button =>

    if (BuildConfig.CALLING_UI_BUTTONS) {
      controller.isMuted.map(!_).onUi(button.setActivated)

      if (BuildConfig.LARGE_VIDEO_CONFERENCE_CALLS) {
        button.setEnabled(true)
        controller.isMuted.map {
          case true  => Some(drawInactiveMicrophone _)
          case false => Some(drawActiveMicrophone _)
        }.onUi {
          case Some(drawFunction) => button.set(drawFunction, R.string.incoming__controls__ongoing__microphone, mute)
          case _     =>
        }
      }

      else {
        controller.isCallEstablished.onUi(button.setEnabled)
        controller.isCallEstablished.onUi(button.setActivated)

        Signal.zip(controller.isMuted, controller.isVideoCall, themeController.currentTheme).map {
        case (true, false, Theme.Light)  => Some(drawInactiveMicrophoneLight _)
        case (true, _, _)                => Some(drawInactiveMicrophone _)
        case (false, false, Theme.Light) => Some(drawActiveMicrophoneLight _)
        case (false, _, _)               => Some(drawActiveMicrophone _)
        case _ => None
      }.onUi {
        case Some(drawFunction) => button.set(drawFunction, R.string.incoming__controls__ongoing__microphone, mute)
        case _ =>
      }
      }
    }
    else {
      controller.isCallEstablished.onUi(button.setEnabled)
      controller.isMuted.onUi(button.setActivated)

      Signal.zip(controller.isVideoCall, controller.isMuted, themeController.currentTheme).map {
        case (true, true, _)             => Some(drawMuteDark _)
        case (true, false, _)            => Some(drawUnmuteDark _)
        case (false, true, Theme.Dark)   => Some(drawMuteDark _)
        case (false, true, Theme.Light)  => Some(drawMuteLight _)
        case (false, false, Theme.Dark)  => Some(drawUnmuteDark _)
        case (false, false, Theme.Light) => Some(drawUnmuteLight _)
        case _ => None
      }.onUi {
        case Some(drawFunction) => button.set(drawFunction, R.string.incoming__controls__ongoing__mute, mute)
        case _ =>
      }
    }
  }

  returning(findById[CallControlButtonView](R.id.video_call)) { button =>
    isVideoBeingSent.onUi(button.setActivated)

    if (BuildConfig.CALLING_UI_BUTTONS) {

      if (BuildConfig.LARGE_VIDEO_CONFERENCE_CALLS) {
        button.setEnabled(true)
        isVideoBeingSent.map {
          case true  => Some(drawActiveCamera _)
          case false => Some(drawInactiveCamera _)
        }.onUi {
          case Some(drawFunction) => button.set(drawFunction, R.string.incoming__controls__ongoing__camera, video)
          case _     =>
        }
      }

      else {
        controller.isCallEstablished.onUi(button.setEnabled)
        Signal.zip(isVideoBeingSent, controller.isVideoCall, themeController.currentTheme).map {
          case (true, false, Theme.Light)  => Some(drawActiveCameraLight _)
          case (true, _, _)                => Some(drawActiveCamera _)
          case (false, false, Theme.Light) => Some(drawInactiveCameraLight _)
          case (false, _, _) => Some(drawInactiveCamera _)
          case _ => None
        }.onUi {
          case Some(drawFunction) => button.set(drawFunction, R.string.incoming__controls__ongoing__camera, video)
          case _ =>
        }
      }

    } else {
      controller.isCallEstablished.onUi(button.setEnabled)
      button.set(WireStyleKit.drawVideocall, R.string.incoming__controls__ongoing__video, video)
    }

  }

  returning(findById[CallControlButtonView](R.id.speaker_flip_call)) { button =>

    if (BuildConfig.CALLING_UI_BUTTONS) {

      if (BuildConfig.LARGE_VIDEO_CONFERENCE_CALLS) {
        button.setEnabled(true)
        Signal.zip(controller.speakerButton.buttonState, isVideoBeingSent).onUi {
          case (true, false)  =>
            button.setActivated(true)
            button.set(drawActiveSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
          case (false, false) =>
            button.setActivated(false)
            button.set(drawInactiveSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
          case (_, true)      =>
            button.set(drawFlip, R.string.incoming__controls__ongoing__flip_camera, flip)
            button.setActivated(false)
        }
      }

      else {

        controller.isCallEstablished.onUi(button.setEnabled)
        Signal.zip(controller.speakerButton.buttonState, isVideoBeingSent, controller.isVideoCall, themeController.currentTheme).onUi {
          case (true, false, false, Theme.Light)  =>
            button.setActivated(true)
            button.set(drawActiveSpeakerLight, R.string.incoming__controls__ongoing__speaker, speaker)
          case (true, false, _, _)                =>
            button.setActivated(true)
            button.set(drawActiveSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
          case (false, false, false, Theme.Light) =>
            button.setActivated(false)
            button.set(drawInactiveSpeakerLight, R.string.incoming__controls__ongoing__speaker, speaker)
          case (false, false, _ , _)              =>
            button.setActivated(false)
            button.set(drawInactiveSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
          case (_, true,  false, Theme.Light)     =>
            button.set(drawFlipLight, R.string.incoming__controls__ongoing__flip_camera, flip)
            button.setActivated(false)
          case (_, true, _, _)                    =>
            button.set(drawFlip, R.string.incoming__controls__ongoing__flip_camera, flip)
            button.setActivated(false)
        }
      }

    }
    else {
      controller.isCallEstablished.onUi(button.setEnabled)
      isVideoBeingSent.onUi {
        case true  =>
          button.set(WireStyleKit.drawFlip, R.string.incoming__controls__ongoing__flip, flip)
        case false =>
          button.set(WireStyleKit.drawSpeaker, R.string.incoming__controls__ongoing__speaker, speaker)
      }
      Signal.zip(controller.speakerButton.buttonState, isVideoBeingSent).onUi {
        case (buttonState, false) => button.setActivated(buttonState)
        case _ => button.setActivated(false)
      }
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
    val perms         = await(permissions.requestPermissions(if (sendingVideo) ListSet(CAMERA, RECORD_AUDIO) else ListSet(RECORD_AUDIO)))
    val audioGranted  = perms.exists(p => p.key.equals(RECORD_AUDIO) && p.granted)
    val callingConvId = await(controller.callConvId.head)
    val callingZms    = await(controller.callingZms.head)

    if (audioGranted)
      callingZms.calling.startCall(callingConvId, await(controller.isVideoCall.head), BuildConfig.FORCE_CONSTANT_BITRATE_CALLS)
    else
      showPermissionsErrorDialog(R.string.calling__cannot_start__title,
        R.string.calling__cannot_start__no_permission__message,
        R.string.calling__cannot_start__cancel__message
        ).flatMap(_ => callingZms.calling.endCall(callingConvId, skipTerminating = true))
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
    controller.speakerButton.click()
  }

  private def video(): Future[Unit] = async {
    onButtonClick ! {}
    val hasCameraPermissions = await(permissions.requestAllPermissions(ListSet(CAMERA)))

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

  val whiteColor = ContextCompat.getColor(context,R.color.white)

  private def drawActiveMicrophone(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveMicrophone, darkTheme)

  private def drawInactiveMicrophone(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame,whiteColor, R.attr.callInactiveMicrophone, darkTheme)

  private def drawActiveCamera(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveCamera, darkTheme)

  private def drawInactiveCamera(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, whiteColor, R.attr.callInactiveCamera, darkTheme)

  private def drawActiveSpeaker(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveSpeaker, darkTheme)

  private def drawInactiveSpeaker(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, whiteColor, R.attr.callInactiveSpeaker, darkTheme)

  private def drawFlip(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, whiteColor, R.attr.callFlip, darkTheme)

  ////TODO to be removed when large conf calling is relased

  private def drawActiveMicrophoneLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveMicrophone, lightTheme)

  private def drawInactiveMicrophoneLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame,color, R.attr.callInactiveMicrophone, lightTheme)

  private def drawActiveCameraLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveCamera, lightTheme)

  private def drawInactiveCameraLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callInactiveCamera, lightTheme)

  private def drawActiveSpeakerLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callActiveSpeaker, lightTheme)

  private def drawInactiveSpeakerLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callInactiveSpeaker, lightTheme)

  private def drawFlipLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callFlip, lightTheme)

  private def drawMuteLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callMutedIcon, lightTheme)

  private def drawMuteDark(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callMutedIcon, darkTheme)

  private def drawUnmuteLight(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callUnmutedIcon, lightTheme)

  private def drawUnmuteDark(canvas: Canvas, targetFrame: RectF, resizing: WireStyleKit.ResizingBehavior, color: Int): Unit =
    drawBitmap(canvas, targetFrame, color, R.attr.callUnmutedIcon, darkTheme)

  ////End

  private def drawBitmap(canvas: Canvas, targetFrame: RectF, color: Int, resourceId: Int, theme: Resources#Theme): Unit =
    ContextUtils.getStyledDrawable(resourceId, theme).foreach { drawable =>
      val paint = returning(new Paint) { p =>
        p.reset()
        p.setFlags(Paint.ANTI_ALIAS_FLAG)
        p.setStyle(Paint.Style.FILL)
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP))
      }
      val bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth, drawable.getIntrinsicHeight, Bitmap.Config.ARGB_8888)
      val c = new Canvas(bitmap)
      drawable.setBounds(0, 0, c.getWidth, c.getHeight)
      drawable.draw(c)
      canvas.drawBitmap(bitmap, targetFrame.left, targetFrame.top, paint)
    }
}
