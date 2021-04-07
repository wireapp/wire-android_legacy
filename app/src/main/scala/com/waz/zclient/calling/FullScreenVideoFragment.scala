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
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, Toast}
import androidx.fragment.app.Fragment
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.{BuildConfig, FragmentHelper, R}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.FullScreenVideoFragment.PARTICIPANT_BUNDLE_KEY
import com.waz.zclient.calling.views.{OtherVideoView, SelfVideoView, UserVideoView}
import com.xuliwen.zoom.ZoomLayout
import com.xuliwen.zoom.ZoomLayout.ZoomLayoutGestureListener

class FullScreenVideoFragment extends FragmentHelper {

  private lazy val controller = inject[CallController]
  private val fullScreenVideoZoomLayout = view[ZoomLayout](R.id.full_screen_video_zoom_layout)
  private val fullScreenVideoContainer = view[FrameLayout](R.id.full_screen_video_container)
  private var participant: Participant = _
  private var userVideoView: UserVideoView = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    if (BuildConfig.ZOOMING_GROUP_CALL) inflater.inflate(R.layout.fragment_full_screen_video, container, false)
    else inflater.inflate(R.layout.fragment_full_screen_video_without_zooming, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    controller.isFullScreenEnabled ! true
    initParticipant()
    initUserVideoView()
    if (BuildConfig.ZOOMING_GROUP_CALL) initVideoZoomLayout()
    initVideoContainer()
    minimizeVideoWhenNotAvailable()
  }

  override def onResume(): Unit = {
    super.onResume()
    if (BuildConfig.ZOOMING_GROUP_CALL) Toast.makeText(getContext, R.string.calling_double_tap_exit_fullscreen_message, Toast.LENGTH_LONG).show()
  }

  override def onBackPressed() = {
    minimizeVideo()
    super.onBackPressed()
  }

  override def onDestroy(): Unit = {
    controller.isFullScreenEnabled ! false
    super.onDestroy()
  }

  def initParticipant(): Unit = {
    val bundle = this.getArguments
    if (bundle != null) {
      participant = bundle.getSerializable(PARTICIPANT_BUNDLE_KEY).asInstanceOf[Participant]
    }
  }

  def initUserVideoView(): Unit = {

    val selfParticipant = controller.selfParticipant.currentValue.get

    userVideoView = if (participant == selfParticipant) new SelfVideoView(getContext, participant)
    else new OtherVideoView(getContext, participant)

    userVideoView.onDoubleClick.onUi { _ =>
      minimizeVideo()
    }
  }

  def initVideoZoomLayout(): Unit = fullScreenVideoZoomLayout.foreach(_.setZoomLayoutGestureListener(new ZoomLayoutGestureListener() {
    override def onScrollBegin(): Unit = {}

    override def onScaleGestureBegin(): Unit = {}

    override def onDoubleTap(): Unit = minimizeVideo()
  }))

  def initVideoContainer(): Unit = fullScreenVideoContainer.foreach(_.addView(userVideoView))

  def minimizeVideoWhenNotAvailable() = controller.allVideoReceiveStates.map(_.getOrElse(participant, VideoState.Unknown)).onUi {
    case VideoState.Started | VideoState.ScreenShare =>
    case _ => minimizeVideo()
  }

  def minimizeVideo(): Unit = {
    controller.isFullScreenEnabled ! false
    fullScreenVideoContainer.foreach(_.removeView(userVideoView))
    getFragmentManager.popBackStackImmediate()
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
