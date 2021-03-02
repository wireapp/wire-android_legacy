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
package com.waz.zclient.calling

import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout}
import androidx.cardview.widget.CardView
import androidx.gridlayout.widget.GridLayout
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{OtherVideoView, SelfVideoView, UserVideoView}
import com.waz.zclient.common.controllers.{ThemeController, ThemeControllingFrameLayout}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.security.SecurityPolicyChecker
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{FragmentHelper, R}
import com.wire.signals.Signal
import com.waz.zclient.calling.CallingFragment.MaxAllVideoPreviews
import com.waz.zclient.calling.CallingFragment.MaxTopSpeakerVideoPreviews
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo

class CallingFragment extends FragmentHelper {
  import Threading.Implicits.Ui

  private lazy val controller             = inject[CallController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val controlsFragment       = ControlsFragment.newInstance
  private lazy val previewCardView        = view[CardView](R.id.preview_card_view)
  private lazy val noActiveSpeakersLayout = view[LinearLayout](R.id.no_active_speakers_layout)

  private lazy val videoGrid = returning(view[GridLayout](R.id.video_grid)) { vh =>

    controller.theme.map(themeController.getTheme).foreach { theme =>
      vh.foreach {
        _.setBackgroundColor(getStyledColor(R.attr.wireBackgroundColor, theme))
      }
    }

    vh.foreach { grid =>

      Signal.zip(videoGridInfo, controller.isFullScreenEnabled, controller.showTopSpeakers, controller.longTermActiveParticipantsWithVideo()).foreach {
        case ((selfParticipant, videoUsers, infos, participants, isVideoBeingSent), false, true, activeParticipantsWithVideo) =>
          refreshVideoGrid(grid, selfParticipant, activeParticipantsWithVideo, infos, participants, isVideoBeingSent, true)
        case ((selfParticipant, videoUsers, infos, participants, isVideoBeingSent), false, false, _) =>
          refreshVideoGrid(grid, selfParticipant, videoUsers, infos, participants, isVideoBeingSent, false)
        case _ =>
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_calling, container, false)) { v =>
      controller.theme(t => v.asInstanceOf[ThemeControllingFrameLayout].theme ! Some(t))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    getChildFragmentManager
      .beginTransaction
      .replace(R.id.controls_layout, controlsFragment, ControlsFragment.Tag)
      .commit

    controller.isCallIncoming.head.foreach {
      if (_) inject[SecurityPolicyChecker].run(getActivity)
    }

    videoGrid

    controller.showTopSpeakers.onChanged { _ =>
      clearVideoGrid()
    }

    Signal.zip(
      controller.showTopSpeakers,
      controller.longTermActiveParticipantsWithVideo().map(_.size > 0),
      controller.controlsVisible,
      controller.isGroupCall
    ).onUi {
      case (true, false, false, true) => noActiveSpeakersLayout.foreach(_.setVisibility(View.VISIBLE))
      case _                          => noActiveSpeakersLayout.foreach(_.setVisibility(View.GONE))
    }

    getView.setOnClickListener(new OnClickListener {
      override def onClick(view: View): Unit = controller.controlsClick(true)
    })
  }

  override def onBackPressed(): Boolean =
    withChildFragmentOpt(R.id.controls_layout) {
      case Some(f: FragmentHelper) if f.onBackPressed()               => true
      case Some(_) if getChildFragmentManager.popBackStackImmediate() => true
      case _ => super.onBackPressed()
    }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    clearVideoGrid()
  }

  private lazy val isVideoBeingSent =
    Signal.zip(controller.allVideoReceiveStates, controller.selfParticipant).map {
      case (videoStates, self) => !videoStates.get(self).contains(VideoState.Stopped)
    }

  private lazy val videoGridInfo = Signal.zip(
    controller.selfParticipant,
    controller.videoUsers,
    controller.participantInfos,
    controller.allParticipants,
    isVideoBeingSent
  )

  private var viewMap = Map[Participant, UserVideoView]()

  private def refreshViews(videoUsers: Seq[Participant], selfParticipant: Participant): Seq[UserVideoView] = {
    def createView(participant: Participant): UserVideoView = returning {
      if (participant == selfParticipant) new SelfVideoView(getContext, participant)
      else new OtherVideoView(getContext, participant)
    } { userView =>
      viewMap = viewMap.updated(participant, userView)
        userView.onDoubleClick.onUi { _ =>

          controller.allParticipants.map(_.size > 2).head.foreach {
            case true =>
              showFullScreenVideo(participant)
              clearVideoGrid()
            case false =>
          }
        }
    }

    videoUsers.map { participant => viewMap.getOrElse(participant, createView(participant)) }
  }

  private def refreshVideoGrid(grid: GridLayout,
                               selfParticipant: Participant,
                               videoUsers: Seq[Participant],
                               infos: Seq[CallParticipantInfo],
                               participants: Set[Participant],
                               isVideoBeingSent: Boolean,
                               showTopSpeakers: Boolean
                              ): Unit = {

    val views = refreshViews(videoUsers, selfParticipant)

      viewMap.get(selfParticipant).foreach { selfView =>
        previewCardView.foreach { cardView =>
          if (!showTopSpeakers && views.size == 2 && participants.size == 2 && isVideoBeingSent) {
            verbose(l"Showing card preview")
            grid.removeView(selfView)
            selfView.setLayoutParams(
              new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
            )
            cardView.addView(selfView)
            cardView.setVisibility(View.VISIBLE)
          } else {
            verbose(l"Hiding card preview")
            cardView.removeAllViews()
            cardView.setVisibility(View.GONE)
          }
        }
      }

    val infoMap = infos.toIdMap

    val gridViews =
      if (showTopSpeakers)
        views.sortWith {
          case (v1, v2) =>
            infoMap(v1.participant.userId).displayName.toLowerCase < infoMap(v2.participant.userId).displayName.toLowerCase
        }.take(MaxTopSpeakerVideoPreviews)
      else
        views.filter {
          case _: SelfVideoView if views.size == 2 && participants.size == 2 && isVideoBeingSent => false
          case _: SelfVideoView if views.size > 1 && !isVideoBeingSent => false
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
      case (participant, _) => !videoUsers.contains(participant)
    }
    viewsToRemove.foreach { case (_, view) => grid.removeView(view) }
    viewMap = viewMap.filter { case (participant, _) => videoUsers.contains(participant) }
  }

  def clearVideoGrid(): Unit = {
    videoGrid.foreach(_.removeAllViews())
    viewMap = Map.empty
  }

  def showFullScreenVideo(participant: Participant): Unit = getChildFragmentManager
    .beginTransaction
    .replace(R.id.full_screen_video_container, FullScreenVideoFragment.newInstance(participant), FullScreenVideoFragment.Tag)
    .commit
}

object CallingFragment {
  val Tag: String = getClass.getSimpleName
  val MaxAllVideoPreviews = 12
  val MaxTopSpeakerVideoPreviews = 4
  def apply(): CallingFragment = new CallingFragment()
}
