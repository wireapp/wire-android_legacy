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
import android.view.ViewGroup
import android.widget.FrameLayout
import com.waz.avs.VideoPreview
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.wire.signals.Signal
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.{R}

class SelfVideoView(context: Context, participant: Participant)
  extends UserVideoView(context, participant) with DerivedLogTag {

  Signal.zip(
    callController.isMuted,
    callController.isInstantActiveSpeaker(participant.qualifiedId.id, participant.clientId),
    accentColorController.accentColor.map(_.color),
    callController.isFullScreenEnabled,
    callController.showTopSpeakers,
    callController.allParticipants.map(_.size > 2)
  ).onUi {
    case (false, true, color, false, false, true)  => {
      updateAudioIndicator(R.drawable.ic_unmuted_video_grid, color, true)
      showActiveSpeakerFrame(color)
    }
    case (false, true, color, false, false, false) => {
      updateAudioIndicator(R.drawable.ic_unmuted_video_grid, color, true)
      hideActiveSpeakerFrame()
    }
    case (false, true, color, _, _, _)             => {
      updateAudioIndicator(R.drawable.ic_unmuted_video_grid, color, true)
      hideActiveSpeakerFrame()
    }
    case (false, false, _, _, _, _)                => {
      updateAudioIndicator(R.drawable.ic_unmuted_video_grid, context.getColor(R.color.white), false)
      hideActiveSpeakerFrame()
    }
    case (true, _, _, _, _, _)                     => {
      updateAudioIndicator(R.drawable.ic_muted_video_grid, context.getColor(R.color.white), false)
      hideActiveSpeakerFrame()
    }
    case _                                         =>
  }

  callController.videoSendState.onUi {
    case VideoState.Started | VideoState.ScreenShare | VideoState.BadConnection =>
      registerHandler(returning(new VideoPreview(getContext)) { v =>
        callController.setVideoPreview(context,null)
        callController.setVideoPreview(context, Some(v))
        v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(v, 1)
      })
    case _ =>
  }

  override lazy val shouldShowInfo: Signal[Boolean] = Signal.zip(pausedTextVisible, callController.isMuted).map {
    case (paused, muted) => paused || muted
  }
}
