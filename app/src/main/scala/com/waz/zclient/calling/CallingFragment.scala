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
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.gridlayout.widget.GridLayout
import com.waz.model.UserId
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
import com.waz.zclient.{BuildConfig, FragmentHelper, R}
import com.wire.signals.{Signal, Subscription}
import com.waz.zclient.calling.CallingFragment.MaxVideoPreviews

class CallingFragment extends FragmentHelper {

  private lazy val controller         = inject[CallController]
  private lazy val themeController    = inject[ThemeController]
  private lazy val controlsFragment   = ControlsFragment.newInstance
  private lazy val previewCardView    = view[CardView](R.id.preview_card_view)
  private var viewMap                 = Map[Participant, UserVideoView]()
  private var videoGridSubscription   = Option.empty[Subscription]
  private lazy val videoGrid = returning(view[GridLayout](R.id.video_grid)) { vh =>
    controller.theme.map(themeController.getTheme).onUi { theme =>
      vh.foreach { _.setBackgroundColor(getStyledColor(R.attr.wireBackgroundColor, theme)) }
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_calling, container, false)) { v =>
      controller.theme(t => v.asInstanceOf[ThemeControllingFrameLayout].theme ! Some(t))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    initVideoGrid()

    getChildFragmentManager
      .beginTransaction
      .replace(R.id.controls_layout, controlsFragment, ControlsFragment.Tag)
      .commit

    controller.isCallIncoming.head.foreach {
      if (_) inject[SecurityPolicyChecker].run(getActivity)
    }(Threading.Ui)

    controller.initVideo.foreach { _ =>
      initVideoGrid()
    }
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

  def initVideoGrid(): Unit = {
    videoGridSubscription = Option(Signal.zip(
      controller.allVideoReceiveStates,
      controller.callingZms.map(zms => Participant(zms.selfUserId, zms.clientId)),
      controller.isVideoCall,
      controller.isCallIncoming,
      controller.participantInfos(),
      controller.otherParticipants
    ).onUi { case (vrs, selfParticipant, videoCall, incoming, infos, participants) =>

      def createView(participant: Participant): UserVideoView = returning {
        if (participant == selfParticipant) new SelfVideoView(getContext, participant)
        else new OtherVideoView(getContext, participant)
      } { v =>
        viewMap = viewMap.updated(participant, v)
        if (BuildConfig.MAXIMIZE_MINIMIZE_VIDEO) {
          v.onDoubleClick.onUi { _ =>
            if (participants.size > 2) {
              showFullScreenVideo(participant)
              clearVideoGrid()
            }
          }
        }
      }

      def findParticipantNameById(userId: UserId): String = infos.find(_.userId == userId).get.displayName

      val isVideoBeingSent = !vrs.get(selfParticipant).contains(VideoState.Stopped)

      videoGrid.foreach { v =>
        val videoUsers = vrs.toSeq.collect {
          case (participant, _) if participant == selfParticipant && videoCall && incoming => participant
          case (participant, VideoState.Started | VideoState.Paused | VideoState.BadConnection | VideoState.NoCameraPermission | VideoState.ScreenShare) => participant
        }
        val views = videoUsers.map { participant => viewMap.getOrElse(participant, createView(participant)) }

        viewMap.get(selfParticipant).foreach { selfView =>
          previewCardView.foreach { cardView =>
            if (views.size == 2 && participants.size == 2 && isVideoBeingSent) {
              verbose(l"Showing card preview")
              cardView.removeAllViews()
              v.removeView(selfView)
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

        val gridViews = views.filter {
          case _: SelfVideoView if views.size == 2 && participants.size == 2 && isVideoBeingSent => false
          case _: SelfVideoView if views.size > 1 && !isVideoBeingSent => false
          case _ => true
        }.sortWith {
          case (_: SelfVideoView, _) => true
          case (_, _: SelfVideoView) => false
          case (v1, v2) => findParticipantNameById(v1.participant.userId).toLowerCase <
            findParticipantNameById(v2.participant.userId).toLowerCase
        }.take(MaxVideoPreviews)

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
            case n if gridViews.size == n + 1 => (n / 2, 0, 2, v.getWidth / 2)
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

          if (userVideoView.getParent == null) v.addView(userVideoView)
        }

        val viewsToRemove = viewMap.filter {
          case (participant, selfView) if participant == selfParticipant => !gridViews.contains(selfView)
          case (participant, _) => !videoUsers.contains(participant)
        }
        viewsToRemove.foreach { case (_, view) => v.removeView(view) }
        viewMap = viewMap.filter { case (participant, _) => videoUsers.contains(participant) }
      }
    })
  }

  def clearVideoGrid(): Unit = {
    videoGrid.foreach(_.removeAllViews())
    viewMap = Map()
    videoGridSubscription.foreach(_.unsubscribe())
  }

  def showFullScreenVideo(participant: Participant): Unit = getChildFragmentManager
    .beginTransaction
    .replace(R.id.full_screen_video_container, FullScreenVideoFragment.newInstance(participant), FullScreenVideoFragment.Tag)
    .commit
}

object CallingFragment {
  val Tag: String = getClass.getSimpleName
  val MaxVideoPreviews = 12
  def apply(): CallingFragment = new CallingFragment()
}
