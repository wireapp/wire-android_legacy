/**
 * Wire
 * Copyright (C) 2020 Wire Swiss GmbH
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

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.{FrameLayout, ImageView, TextView}
import androidx.cardview.widget.CardView
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.model.Picture
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.utils.returning
import com.waz.zclient.ViewHelper
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.utils.ContextUtils.{getColor, getString}
import com.wire.signals.{EventStream, Signal}
import com.waz.zclient.R
import com.waz.zclient.utils.RichView
import com.waz.threading.Threading._
import com.waz.zclient.common.controllers.global.AccentColorController

abstract class UserVideoView(context: Context, val participant: Participant) extends FrameLayout(context, null, 0) with ViewHelper {
  protected lazy val callController: CallController = inject[CallController]
  protected lazy val accentColorController = inject[AccentColorController]

  inflate(R.layout.video_call_info_view)

  def shouldShowInfo: Signal[Boolean]

  val onDoubleClick = EventStream[Unit]()

  this.onClick({
    callController.controlsClick(true)
  }, {
    onDoubleClick ! {}
  })

  private val pictureId: Signal[Picture] = for {
    z             <- callController.callingZms
    Some(picture) <- z.usersStorage.signal(participant.userId).map(_.picture)
  } yield picture

  protected val audioStatusImageView = findById[ImageView](R.id.audio_status_image_view)

  /*protected val imageView = returning(findById[ImageView](R.id.image_view)) { view =>
    pictureId.onUi(BackgroundRequest(_).into(view))
  }*/

  protected val pausedText = findById[TextView](R.id.paused_text_view)

  private val participantInfoCardView = findById[CardView](R.id.participant_info_card_view)

  protected val stateMessageText = callController.stateMessageText(participant)
  stateMessageText.onUi(msg => pausedText.setText(msg.getOrElse("")))

  protected val pausedTextVisible = stateMessageText.map(_.exists(_.nonEmpty))
  pausedTextVisible.onUi(pausedText.setVisible)

  protected val videoCallInfo = returning(findById[View](R.id.video_call_info)) {
    _.setBackgroundColor(getColor(R.color.black_16))
  }

  protected val participantInfo: Signal[Option[CallParticipantInfo]] =
    for {
      isGroup <- callController.isGroupCall
      infos   <- if (isGroup) callController.participantInfos else Signal.const(Vector.empty)
    } yield infos.find(_.id == participant.userId)

  protected val nameTextView = returning(findById[TextView](R.id.name_text_view)) { view =>
    participantInfo.onUi {
      case Some(p) if p.isSelf => view.setText(getString(R.string.calling_self, p.displayName))
      case Some(p)             => view.setText(p.displayName)
      case _                   =>
    }
  }

  Signal.zip(
    callController.isGroupCall,
    callController.controlsVisible,
    callController.otherParticipants.map(_.size)
  ).map {
    case (true, false, 0 | 1 | 2) => View.GONE
    case (true, false, _)         => View.VISIBLE
    case _                        => View.GONE
  }.onUi(participantInfoCardView.setVisibility)

  private lazy val allVideoStates =  callController.allVideoReceiveStates.map(_.getOrElse(participant, VideoState.Unknown))

  protected def registerHandler(view: View): Unit = {
    allVideoStates.onUi {
      case VideoState.Paused | VideoState.Stopped => view.fadeOut()
      case _                                      => view.fadeIn()
    }

    Signal.zip(callController.isFullScreenEnabled, allVideoStates).onUi {
      case (true, _)                       => videoShouldFit(view)
      case (false, VideoState.ScreenShare) => videoShouldFit(view)
      case (false, VideoState.Started)     => videoShouldFill(view)
      case _                               =>
    }
  }

  def videoShouldFill(view: View) = view match {
    case videoRenderer: VideoRenderer =>
      videoRenderer.setShouldFill(true)
      videoRenderer.setFillRatio(1.0f)
    case videoPreview: VideoPreview =>
      videoPreview.setShouldFill(true)
      videoPreview.setAspectRatio(1.0f)
    case _ =>
  }

  def videoShouldFit(view: View) = view match {
    case videoRenderer: VideoRenderer =>
      videoRenderer.setShouldFill(false)
      videoRenderer.setFillRatio(1.5f)
    case videoPreview: VideoPreview =>
      videoPreview.setShouldFill(false)
      videoPreview.setAspectRatio(1.5f)
    case _ =>
  }

    Signal.zip(callController.controlsVisible, shouldShowInfo, callController.isCallIncoming).onUi {
    case (_, true, true) |
         (false, true, _) => videoCallInfo.fadeIn()
    case _                => videoCallInfo.fadeOut()
  }

  def updateAudioIndicator(imageResource: Int, color: Int, isAnimated: Boolean): Unit = {
    audioStatusImageView.setImageResource(imageResource)
    audioStatusImageView.setColorFilter(color)
    if (isAnimated) audioStatusImageView.startAnimation(AnimationUtils.loadAnimation(getContext, R.anim.infinite_fade_in_fade_out))
    else audioStatusImageView.clearAnimation()
  }

  def showActiveSpeakerFrame(color: Int): Unit = {
    val border = new GradientDrawable()
    border.setColor(getColor(R.color.black))
    border.setStroke(3, color)
    setBackground(border)
    getChildAt(1).setMargin(3,3,3,3)
  }

  def hideActiveSpeakerFrame(): Unit = {
    setBackgroundColor(getColor(R.color.black))
    getChildAt(1).setMargin(0,0,0,0)
  }

}
