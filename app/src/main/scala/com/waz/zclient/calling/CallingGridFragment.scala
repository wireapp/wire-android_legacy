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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import com.waz.model.{QualifiedId, UserId}
import com.waz.model.otr.ClientId
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading.Implicits.Ui
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper
import com.waz.zclient.calling.NewCallingFragment.{MaxTopSpeakerVideoPreviews, NbParticipantsOneOneCall}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.calling.views.{OtherVideoView, SelfVideoView, UserVideoView}
import com.wire.signals.Signal
import com.xuliwen.zoom.ZoomLayout
import com.xuliwen.zoom.ZoomLayout.ZoomLayoutGestureListener
import com.waz.zclient.R
import com.waz.zclient.calling.CallingGridFragment.PAGINATION_BUNDLE_KEY
import com.waz.zclient.calling.AllParticipantsAdapter.MAX_PARTICIPANTS_PER_PAGE
import com.waz.zclient.utils.{ContextUtils, RichView}


class CallingGridFragment extends FragmentHelper {

  private lazy val callController           = inject[CallController]
  private lazy val zoomLayout               = view[ZoomLayout](R.id.zoom_layout)
  private lazy val videoGrid                = view[GridLayout](R.id.video_grid)
  private var pageNumber: Int               = 0

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_calling_grid, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    initPageNumber()
    initZoomLayout()
    initCallingGrid()
    observeParticipantsCount()
    initClickForRootView()
  }

  override def onDestroyView(): Unit = {
    clearVideoGrid()
    super.onDestroyView()
  }

  private def initPageNumber(): Unit = {
    Option(getArguments).foreach { bundle =>
      pageNumber = bundle.getInt(PAGINATION_BUNDLE_KEY)
    }
  }

  private def initZoomLayout(): Unit =
    zoomLayout.foreach(_.setZoomLayoutGestureListener(new ZoomLayoutGestureListener() {

      override def onDoubleTap(): Unit = {}

      override def onSingleTap(): Unit = callController.controlsClick(true)

      override def onScrollBegin(): Unit = {}

      override def onScaleGestureBegin(): Unit = {}
    }))

  private def initCallingGrid(): Unit = {

    val participantsData = Signal.zip(
      callController.selfParticipant.map(_.qualifiedId),
      callController.selfParticipant.map(_.clientId),
      callController.participantsInfo,
      callController.allParticipants,
      callController.longTermActiveParticipants()
    )

    videoGrid.foreach { grid =>
      Signal.zip(
        participantsData,
        callController.isFullScreenEnabled,
        callController.showTopSpeakers,
        callController.allParticipants.map(_.size)
      ).foreach {
        case ((selfUserId, selfClientId, participantsInfo, participants, activeParticipants), false, true, _) =>
          refreshVideoGrid(grid, selfUserId, selfClientId, activeParticipants, participantsInfo, participants, true)
        case ((selfUserId, selfClientId, participantsInfo, participants, _), false, false, size) =>

          val startIndex = pageNumber * MAX_PARTICIPANTS_PER_PAGE
          val endIndex = startIndex + MAX_PARTICIPANTS_PER_PAGE

          val participantsToShow = (orderedParticipants(participants, participantsInfo.toIdMap, selfUserId, selfClientId).slice(startIndex, endIndex), size) match {
            case (ps, 2) => ps.filter(_.clientId != selfClientId)
            case (ps, _) => ps
          }

          refreshVideoGrid(grid, selfUserId, selfClientId, participantsToShow, participantsInfo, participants, false)
        case _ =>
      }
    }
  }

  private def orderedParticipants(participants: Set[Participant], infoMap: Map[UserId, CallParticipantInfo], selfQualifiedId: QualifiedId, selfClientId: ClientId): Seq[Participant] =
    participants.toSeq.sortWith {
      case (p, _) if p.qualifiedId.id == selfQualifiedId.id && p.clientId == selfClientId => true
      case (_, p) if p.qualifiedId.id == selfQualifiedId.id && p.clientId == selfClientId => false
      case (p, _) if p.qualifiedId.id == selfQualifiedId.id => true
      case (_, p) if p.qualifiedId.id == selfQualifiedId.id => false
      case (p1, p2) if isVideoUser(infoMap(p1.qualifiedId.id)) && !isVideoUser(infoMap(p2.qualifiedId.id)) => true
      case (p1, p2) if !isVideoUser(infoMap(p1.qualifiedId.id)) && isVideoUser(infoMap(p2.qualifiedId.id)) => false
      case (p1, p2) => infoMap(p1.qualifiedId.id).displayName.toLowerCase < infoMap(p2.qualifiedId.id).displayName.toLowerCase
    }

  private def observeParticipantsCount(): Unit =
    callController.allParticipants.map(_.size).onUi {
      case NbParticipantsOneOneCall =>
        enableZooming()
        displayPinchToZoomIndication()
      case _ => disableZooming()
    }

  private def enableZooming(): Unit = zoomLayout.foreach(_.setEnabled(true))

  private def disableZooming(): Unit = zoomLayout.foreach(_.setEnabled(false))

  private def displayPinchToZoomIndication(): Unit =
    Toast.makeText(getContext, R.string.calling_pinch_to_zoom_message, Toast.LENGTH_LONG).show()


  private def showFullScreenVideo(participant: Participant): Unit = getParentFragment.getChildFragmentManager
    .beginTransaction
    .replace(R.id.full_screen_video_container, FullScreenVideoFragment.newInstance(participant), FullScreenVideoFragment.Tag)
    .commit


  private def refreshVideoGrid(grid: GridLayout,
                               selfQualifiedId: QualifiedId,
                               selfClientId: ClientId,
                               participantsToShow: Seq[Participant],
                               info: Seq[CallParticipantInfo],
                               allParticipants: Set[Participant],
                               showTopSpeakers: Boolean
                              ): Unit = {

    clearVideoGrid()

    val views = refreshViews(participantsToShow, selfQualifiedId, selfClientId)

    val infoMap = info.toIdMap

    val gridViews =
      if (showTopSpeakers)
        views.sortWith {
          case (v1, v2) =>
            infoMap(v1.participant.qualifiedId.id).displayName.toLowerCase < infoMap(v2.participant.qualifiedId.id).displayName.toLowerCase
        }.take(MaxTopSpeakerVideoPreviews)
      else
        views.filter {
          case _: SelfVideoView if views.size == 2 && allParticipants.size == 2  => false
          case _ => true
        }


    gridViews.zipWithIndex.foreach { case (userVideoView, index) =>

      import gridViews._

      val (row, col, span, width) = if (ContextUtils.isInLandscape) index match {
        case 0 if size == 1 => (0, 0, 1, 0)
        case 0 if size == 2 => (0, 0, 2, 0)
        case 0 if size > 2  => (0, 0, 1, 0)

        case 1 if size == 1 => (0, 1, 1, 0)
        case 1 if size == 2 => (1, 0, 2, 0)
        case 1 if size > 2  => (0, 1, 1, 0)

        case 2 if size == 3 => (1, 0, 2, grid.getWidth / 2)
        case 2 if size == 4 => (1, 0, 1, 0)
        case 2 if size > 4  => (0, 2, 1, 0)

        case 3 if size == 4 => (1, 1, 1, 0)
        case 3 if size == 5 => (1, 0, 2, grid.getWidth / 3)
        case 3 if size == 6 => (1, 0, 1, 0)
        case 3 if size > 6  => (0, 3, 1, 0)

        case 4 if size == 5 => (1, 1, 2, grid.getWidth / 3)
        case 4 if size == 6 => (1, 1, 1, 0)
        case 4 if size == 7 => (1, 0, 2, grid.getWidth / 4)
        case 4 if size == 8 => (1, 0, 1, 0)

        case 5 if size == 6 => (1, 2, 1, 0)
        case 5 if size == 7 => (1, 1, 2, grid.getWidth / 4)
        case 5 if size == 8 => (1, 1, 1, 0)

        case 6 if size == 7 => (1, 2, 2, grid.getWidth / 4)
        case 6 if size == 8 => (1, 2, 1, 0)

        case 7 if size == 8 => (1, 3, 1, 0)

      }
      else index match {

        case 0 if size == 2 => (0, 0, 2, 0)
        case 0 => (0, 0, 1, 0)
        case 1 if size == 2 => (1, 0, 2, 0)
        case 1 => (0, 1, 1, 0)
        // The max number of columns is 2 and the max number of rows is undefined
        // if the index of the video preview is even, display it in row n/2, column 1 , span 1 , width match_parent(0)
        case n if n % 2 != 0 => (n / 2, 1, 1, 0)
        // else if the gridViews size is n+1 , display it in row n/2, column 0 , span 2, , width view size / 2
        case n if size == n + 1 => (n / 2, 0, 2, grid.getWidth / 2)
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

  }

  def isVideoUser(callParticipantInfo: CallParticipantInfo): Boolean = callParticipantInfo.isVideoEnabled || callParticipantInfo.isScreenShareEnabled

  private def refreshViews(participantsToShow: Seq[Participant], selfQualifiedId: QualifiedId, selfClientId: ClientId): Seq[UserVideoView] = {

    def createView(participant: Participant): UserVideoView = returning {
      if (participant.clientId == selfClientId)
        new SelfVideoView(getContext, Participant(qualifiedId = selfQualifiedId, clientId = selfClientId))
      else new OtherVideoView(getContext, participant)
    } { userView =>
      userView.onDoubleClick.onUi { _ =>
        callController.allParticipants.map(_.size > 2).head.foreach {
          case true =>
            showFullScreenVideo(participant)
            clearVideoGrid()
          case false =>
        }
      }
    }

    callController.allParticipants.map(_.size != 2).head.foreach {
      case true =>
        callController.isSelfViewVisible ! participantsToShow.exists(_.clientId == selfClientId)
      case false =>
    }

    participantsToShow.map { participant => createView(participant) }
  }

  private def clearVideoGrid(): Unit = {
    videoGrid.foreach(_.removeAllViews())
  }

  private def initClickForRootView(): Unit = getView.onClick {
    callController.controlsClick(true)
  }

}

object CallingGridFragment {
  val Tag = classOf[CallingGridFragment].getName
  val PAGINATION_BUNDLE_KEY = "pagination"

  def newInstance(pageNumber: Int): Fragment = returning(new CallingGridFragment) {
    _.setArguments(returning(new Bundle) { bundle =>
      bundle.putInt(PAGINATION_BUNDLE_KEY, pageNumber)
    })
  }
}
