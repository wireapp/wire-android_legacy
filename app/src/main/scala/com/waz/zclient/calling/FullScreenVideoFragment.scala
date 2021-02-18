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
package com.waz.zclient.calling

import android.os.Bundle
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, Toast}
import androidx.fragment.app.Fragment
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.FullScreenVideoFragment.PARTICIPANT_BUNDLE_KEY
import com.waz.zclient.calling.views.{OtherVideoView, SelfVideoView, UserVideoView}
import com.waz.zclient.R
import com.shopgun.android.zoomlayout.ZoomLayout
import com.shopgun.android.zoomlayout.ZoomLayout.{OnDoubleTapListener, OnTapListener}

class FullScreenVideoFragment extends FragmentHelper {

  private lazy val controller = inject[CallController]
  private lazy val fullScreenVideoZoomLayout = returning(view[ZoomLayout](R.id.full_screen_video_zoom_layout)) { vh =>
    val bundle = this.getArguments
    if (bundle != null) {
      val participant = bundle.getSerializable(PARTICIPANT_BUNDLE_KEY).asInstanceOf[Participant]
      controller.isFullScreenEnabled ! true

      vh.foreach { zoomLayout =>

        view[FrameLayout](R.id.full_screen_video_container).foreach { container =>

          val selfParticipant = controller.callingZms.map(zms => Participant(zms.selfUserId, zms.clientId)).currentValue.get

          val userVideoView = if (participant == selfParticipant) new SelfVideoView(getContext, participant)
          else new OtherVideoView(getContext, participant)

          userVideoView.onDoubleClick.onUi { _ =>
            minimizeVideo(container, userVideoView)
          }


          zoomLayout.addOnTapListener(new OnTapListener {
            override def onTap(view: ZoomLayout, info: ZoomLayout.TapInfo): Boolean = {
              controller.controlsClick(true)
              true
            }
          })

          zoomLayout.addOnDoubleTapListener(new OnDoubleTapListener {
            override def onDoubleTap(view: ZoomLayout, info: ZoomLayout.TapInfo): Boolean = {
              minimizeVideo(container, userVideoView)
              true
            }
          })

          container.addView(userVideoView)

          controller.allVideoReceiveStates.map(_.getOrElse(participant, VideoState.Unknown)).onUi {
            case VideoState.Started | VideoState.ScreenShare =>
            case _ => minimizeVideo(container, userVideoView)
          }
        }
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_full_screen_video, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    fullScreenVideoZoomLayout
  }

  override def onResume(): Unit = {
    super.onResume()
    val toast = Toast.makeText(getContext, R.string.calling_double_tap_exit_fullscreen_message, Toast.LENGTH_LONG)
    toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0)
    toast.show()
  }
  def minimizeVideo(container: FrameLayout, userVideoView: UserVideoView): Unit = {
    container.removeView(userVideoView)
    getFragmentManager.popBackStack()
    controller.isFullScreenEnabled ! false
  }
}

object FullScreenVideoFragment {
  val Tag = classOf[FullScreenVideoFragment].getName
  val PARTICIPANT_BUNDLE_KEY = "participant"

  def newInstance(participant: Participant): Fragment = returning(new FullScreenVideoFragment) {
    _.setArguments(returning(new Bundle) { bundle =>
      bundle.putSerializable(PARTICIPANT_BUNDLE_KEY, participant)
    })
  }
}
