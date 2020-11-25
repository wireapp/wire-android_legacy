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
import com.waz.avs.VideoRenderer
import com.waz.service.call.CallInfo.Participant
import com.waz.utils.returning
import com.waz.threading.Threading._
import com.waz.zclient.R
import com.wire.signals.Signal

class OtherVideoView(context: Context, participant: Participant) extends UserVideoView(context, participant) {

  participantInfo.onUi {
    case Some(info) =>
      if (info.isMuted) audioStatusImageView.setImageResource(R.drawable.ic_muted_video_grid)
      else audioStatusImageView.setImageResource(R.drawable.ic_unmuted_video_grid)
    case _ =>
  }

  override lazy val shouldShowInfo: Signal[Boolean] = pausedTextVisible

  registerHandler(returning(new VideoRenderer(getContext, participant.userId.str, participant.clientId.str, false)) { v =>
    v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(v, 1)
  })
}
