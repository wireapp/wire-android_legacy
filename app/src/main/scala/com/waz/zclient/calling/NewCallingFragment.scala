/**
 * Wire
 * Copyright (C) 2021 Wire Swiss GmbH
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
import android.widget.{FrameLayout, LinearLayout, Toast}
import androidx.cardview.widget.CardView
import androidx.gridlayout.widget.GridLayout
import com.waz.avs.VideoPreview
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.calling.NewCallingFragment.{MaxAllVideoPreviews, MaxTopSpeakerVideoPreviews, NbParticipantsOneOneCall}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.calling.views.{OtherVideoView, SelfVideoView, UserVideoView}
import com.waz.zclient.common.controllers.{ThemeController, ThemeControllingFrameLayout}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityPolicyChecker
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.wire.signals.Signal
import com.xuliwen.zoom.ZoomLayout
import com.xuliwen.zoom.ZoomLayout.ZoomLayoutGestureListener
import Threading.Implicits.Ui


class NewCallingFragment extends FragmentHelper {

  private lazy val controlsFragment         = ControlsFragment.newInstance
  private lazy val callController           = inject[CallController]
  private lazy val themeController          = inject[ThemeController]
  private lazy val previewCardView          = view[CardView](R.id.preview_card_view)
  private lazy val noActiveSpeakersLayout   = view[LinearLayout](R.id.no_active_speakers_layout)
  private lazy val parentLayout             = view[FrameLayout](R.id.parent_layout)
  private lazy val zoomLayout               = view[ZoomLayout](R.id.zoom_layout)
  private lazy val videoGrid                = view[GridLayout](R.id.video_grid)
  private var viewMap                       = Map[Participant, UserVideoView]()
  private lazy val videoPreview             = new VideoPreview(getContext) { preview =>
    preview.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    preview.setElevation(0)
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_new_calling, container, false)) { v =>
      callController.theme.foreach(t => v.asInstanceOf[ThemeControllingFrameLayout].theme ! Some(t))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    runSecurityPolicyChecker()
    initCallingOverlay()
    initZoomLayout()
    initVideoGrid()
    initNoActiveSpeakersLayout()
    displayFullScreenModeIndication()
    observeParticipantsCount()
    manageVideoPreview()
    initClickForRootView()

  }

  override def onBackPressed(): Boolean =
    withChildFragmentOpt(R.id.controls_layout) {
      case Some(f: FragmentHelper) if f.onBackPressed()               => true
      case Some(_) if getChildFragmentManager.popBackStackImmediate() => true
      case _ => super.onBackPressed()
    }

  override def onDestroyView(): Unit = {
    clearVideoGrid()
    super.onDestroyView()
  }

  private def runSecurityPolicyChecker(): Unit =
    callController.isCallIncoming.head.foreach {
      if (_) inject[SecurityPolicyChecker].run(getActivity)
    }

  private def initCallingOverlay(): Unit = getChildFragmentManager
    .beginTransaction
    .replace(R.id.controls_layout, controlsFragment, ControlsFragment.Tag)
    .commit

  private def initZoomLayout(): Unit = {
    zoomLayout.foreach(_.setZoomLayoutGestureListener(new ZoomLayoutGestureListener() {

      override def onDoubleTap(): Unit = {}

      override def onSingleTap(): Unit = callController.controlsClick(true)

      override def onScrollBegin(): Unit = {}

      override def onScaleGestureBegin(): Unit = {}
    }))
  }

  private def initVideoGrid(): Unit = {
    callController.theme.map(themeController.getTheme).foreach { theme =>
      videoGrid.foreach {
        _.setBackgroundColor(getStyledColor(R.attr.wireBackgroundColor, theme))
      }
    }

    val participantsData = Signal.zip(
      callController.selfParticipant,
      callController.participantsInfo,
      callController.allParticipants,
      callController.longTermActiveParticipants()
    )

    videoGrid.foreach { grid =>
      Signal.zip(
        participantsData,
        callController.isFullScreenEnabled,
        callController.showTopSpeakers
      ).foreach {
        case ((selfParticipant, participantsInfo, participants, activeParticipants), false, true) =>
          refreshVideoGrid(grid, selfParticipant, activeParticipants, participantsInfo, participants, true)
        case ((selfParticipant, participantsInfo, participants, _), false, false) =>
          refreshVideoGrid(grid, selfParticipant, participants.toSeq, participantsInfo, participants, false)
        case _ =>
      }
    }
  }

  private def initNoActiveSpeakersLayout(): Unit = {

    Signal.zip(
      callController.showTopSpeakers,
      callController.longTermActiveParticipants().map(_.size > 0),
      callController.controlsVisible,
      callController.isGroupCall
    ).onUi {
      case (true, false, false, true) => noActiveSpeakersLayout.foreach(_.setVisibility(View.VISIBLE))
      case _ => noActiveSpeakersLayout.foreach(_.setVisibility(View.GONE))
    }
  }

  private def displayFullScreenModeIndication(): Unit =
    callController.isGroupCall.onChanged.foreach {
      case true =>
        Toast.makeText(getContext, R.string.calling_double_tap_enter_fullscreen_message, Toast.LENGTH_LONG).show()
      case _ =>
    }

  private def observeParticipantsCount(): Unit = {
    callController.allParticipants.map(_.size).onUi {
      case NbParticipantsOneOneCall =>
        enableZooming()
        displayPinchToZoomIndication()
      case _ => disableZooming()
    }
  }

  private def enableZooming(): Unit = zoomLayout.foreach(_.setEnabled(true))

  private def disableZooming(): Unit = zoomLayout.foreach(_.setEnabled(false))

  private def displayPinchToZoomIndication(): Unit =
    Toast.makeText(getContext, R.string.calling_pinch_to_zoom_message, Toast.LENGTH_LONG).show()

  private def manageVideoPreview(): Unit = {
    Signal.zip(callController.isSelfViewVisible, callController.videoSendState).onUi {
      case (false, VideoState.Started | VideoState.ScreenShare | VideoState.BadConnection) =>
        stopVideoPreview()
        startVideoPreview()
        attachVideoPreviewToParentLayout()
      case (false, _) =>
        stopVideoPreview()
        detachVideoPreviewFromParentLayout()
      case (true, _) =>
        detachVideoPreviewFromParentLayout()
    }
  }

  private def stopVideoPreview(): Unit = callController.setVideoPreview(null)

  private def startVideoPreview(): Unit =
    callController.setVideoPreview(Some(videoPreview))

  private def attachVideoPreviewToParentLayout(): Unit =
    parentLayout.foreach(_.addView(videoPreview))

  private def detachVideoPreviewFromParentLayout(): Unit =
    parentLayout.foreach(_.removeView(videoPreview))

  private def initClickForRootView(): Unit = getView.onClick {
    callController.controlsClick(true)
  }

  private def showFullScreenVideo(participant: Participant): Unit = getChildFragmentManager
    .beginTransaction
    .replace(R.id.full_screen_video_container, FullScreenVideoFragment.newInstance(participant), FullScreenVideoFragment.Tag)
    .commit


  private def refreshVideoGrid(grid: GridLayout,
                               selfParticipant: Participant,
                               participantsToShow: Seq[Participant],
                               info: Seq[CallParticipantInfo],
                               allParticipants: Set[Participant],
                               showTopSpeakers: Boolean
                              ): Unit = {

    val views = refreshViews(participantsToShow, selfParticipant)

    manageFloatingSelfPreview(grid, selfParticipant, showTopSpeakers, views.size, allParticipants.size)

    val infoMap = info.toIdMap

    val gridViews =
      if (showTopSpeakers)
        views.sortWith {
          case (v1, v2) =>
            infoMap(v1.participant.userId).displayName.toLowerCase < infoMap(v2.participant.userId).displayName.toLowerCase
        }.take(MaxTopSpeakerVideoPreviews)
      else
        views.filter {
          case _: SelfVideoView if views.size == 2 && allParticipants.size == 2  => false
          case _ => true
        }.sortWith {
          case (_: SelfVideoView, _) => true
          case (_, _: SelfVideoView) => false
          case (v1, v2) =>
            infoMap(v1.participant.userId).displayName.toLowerCase < infoMap(v2.participant.userId).displayName.toLowerCase
        }.take(MaxAllVideoPreviews)


    gridViews.zipWithIndex.foreach { case (userVideoView, index) =>
      val (row, col, span, width) = index match {
        case 0 if gridViews.size == 2 => (0, 0, 2, 0)
        case 0 => (0, 0, 1, 0)
        case 1 if gridViews.size == 2 => (1, 0, 2, 0)
        case 1 => (0, 1, 1, 0)
        // The max number of columns is 2 and the max number of rows is undefined
        // if the index of the video preview is even, display it in row n/2, column 1 , span 1 , width match_parent(0)
        case n if n % 2 != 0 => (n / 2, 1, 1, 0)
        // else if the gridViews size is n+1 , display it in row n/2, column 0 , span 2, , width view size / 2
        case n if gridViews.size == n + 1 => (n / 2, 0, 2, grid.getWidth / 2)
        // else display it in row n/2, column 0 , span 1, , width match_parent(0)
        case n => (n / 2, 0, 1, 0)
      }

      val columnAlignment = width match {
        case 0 => GridLayout.FILL
        case _ => GridLayout.CENTER
      }

      userVideoView.setLayoutParams(returning(new GridLayout.LayoutParams()) { params =>
        params.width = width
        params.height = 0
        params.rowSpec = GridLayout.spec(row, 1, GridLayout.FILL, 1f)
        params.columnSpec = GridLayout.spec(col, span, columnAlignment, 1f)
      })

      if (Option(userVideoView.getParent).isEmpty) grid.addView(userVideoView)
    }

    val viewsToRemove = viewMap.filter {
      case (participant, selfView) if participant == selfParticipant => !gridViews.contains(selfView)
      case (participant, _) => !participantsToShow.contains(participant)
    }

    viewsToRemove.foreach { case (_, view) => grid.removeView(view) }

    viewMap = viewMap.filter { case (participant, _) => participantsToShow.contains(participant) }
  }

  private def refreshViews(participantsToShow: Seq[Participant], selfParticipant: Participant): Seq[UserVideoView] = {

    def createView(participant: Participant): UserVideoView = returning {
      if (participant == selfParticipant) new SelfVideoView(getContext, selfParticipant)
      else new OtherVideoView(getContext, participant)
    } { userView =>
      viewMap = viewMap.updated(participant, userView)
      userView.onDoubleClick.onUi { _ =>
        callController.allParticipants.map(_.size > 2).head.foreach {
          case true =>
            showFullScreenVideo(participant)
            clearVideoGrid()
          case false =>
        }
      }
    }

    if (participantsToShow.contains(selfParticipant)) callController.isSelfViewVisible ! true
    else callController.isSelfViewVisible ! false

    participantsToShow.map { participant => viewMap.getOrElse(participant, createView(participant)) }
  }


  private def clearVideoGrid(): Unit = {
    videoGrid.foreach(_.removeAllViews())
    viewMap = Map.empty
  }

  private def manageFloatingSelfPreview(grid: GridLayout, selfParticipant: Participant, showTopSpeakers: Boolean, viewsCount: Int, ParticipantsCount: Int): Unit = {
    viewMap.get(selfParticipant).foreach { selfView =>
      previewCardView.foreach { cardView =>
        if (!showTopSpeakers && viewsCount == 2 && ParticipantsCount == 2) {
          showFloatingSelfPreview(grid, selfView, cardView)
        } else {
          hideFloatingSelfPreview(cardView)
        }
      }
    }
  }

  private def showFloatingSelfPreview(grid: GridLayout, selfVideoView: UserVideoView, cardView: CardView): Unit = {
    verbose(l"Showing card preview")
    grid.removeView(selfVideoView)
    selfVideoView.setLayoutParams(
      new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
    cardView.addView(selfVideoView)
    cardView.setVisibility(View.VISIBLE)
  }

  private def hideFloatingSelfPreview(cardView: CardView): Unit = {
    verbose(l"Hiding card preview")
    cardView.removeAllViews()
    cardView.setVisibility(View.GONE)
  }

}

object NewCallingFragment {
  val Tag: String = getClass.getSimpleName
  val MaxAllVideoPreviews = 12
  val MaxTopSpeakerVideoPreviews = 4
  val NbParticipantsOneOneCall: Int = 2
  def apply(): NewCallingFragment = new NewCallingFragment()
}



