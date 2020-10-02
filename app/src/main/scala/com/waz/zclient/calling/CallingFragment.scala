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

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, ImageView, TextView}
import androidx.cardview.widget.CardView
import androidx.gridlayout.widget.GridLayout
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Picture, UserId}
import com.waz.service.call.Avs.VideoState
import com.waz.service.call.CallInfo.Participant
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.{ThemeController, ThemeControllingFrameLayout}
import com.waz.zclient.glide.BackgroundRequest
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, ViewHelper}
import com.wire.signals.{SerialDispatchQueue, Signal}

abstract class UserVideoView(context: Context, val participant: Participant) extends FrameLayout(context, null, 0) with ViewHelper {
  protected lazy val controller: CallController = inject[CallController]
  private implicit val dispatcher = SerialDispatchQueue(name = s"UserVideoView-$participant")

  inflate(R.layout.video_call_info_view)

  private val pictureId: Signal[Picture] = for {
    z             <- controller.callingZms
    Some(picture) <- z.usersStorage.signal(participant.userId).map(_.picture)
  } yield picture

  protected val audioStatusImageView = findById[ImageView](R.id.audio_status_image_view)

  protected val imageView = returning(findById[ImageView](R.id.image_view)) { view =>
    pictureId.onUi(BackgroundRequest(_).into(view))
  }

  protected val pausedText = findById[TextView](R.id.paused_text_view)

  private val participantInfoCardView = findById[CardView](R.id.participant_info_card_view)

  protected val stateMessageText = controller.stateMessageText(participant)
  stateMessageText.onUi(msg => pausedText.setText(msg.getOrElse("")))

  protected val pausedTextVisible = stateMessageText.map(_.exists(_.nonEmpty))
  pausedTextVisible.onUi(pausedText.setVisible)

  protected val videoCallInfo = returning(findById[View](R.id.video_call_info)) {
    _.setBackgroundColor(getColor(R.color.black_16))
  }

   protected val participantInfo: Signal[Option[CallParticipantInfo]] =
    for {
      isGroup <- controller.isGroupCall
      infos   <- if (isGroup) controller.participantInfos() else Signal.const(Vector.empty)
    } yield infos.find(_.userId == participant.userId)

  protected val nameTextView = returning(findById[TextView](R.id.name_text_view)) { view =>
    participantInfo.onUi {
      case Some(p) if p.isSelf => view.setText(getString(R.string.calling_self, p.displayName))
      case Some(p)             => view.setText(p.displayName)
      case _                   =>
    }
  }

  Signal.zip(controller.isGroupCall, controller.controlsVisible).map {
    case (true, false) => View.VISIBLE
    case _             => View.GONE
  }.onUi(participantInfoCardView.setVisibility)

  protected def registerHandler(view: View) = {
    controller.allVideoReceiveStates.map(_.getOrElse(participant, VideoState.Unknown)).onUi {
      case VideoState.Paused | VideoState.Stopped => view.fadeOut()
      case _                 => view.fadeIn()
    }
    view match {
      case vr: VideoRenderer =>
        controller.allVideoReceiveStates.map(_.getOrElse(participant, VideoState.Unknown)).onUi {
          case VideoState.ScreenShare =>
            vr.setShouldFill(false)
            vr.setFillRatio(1.5f)
          case _ =>
            vr.setShouldFill(true)
            vr.setFillRatio(1.0f)
        }
      case _ =>
    }
  }

  Signal.zip(controller.controlsVisible, shouldShowInfo, controller.isCallIncoming).onUi {
    case (_, true, true) |
         (false, true, _) => videoCallInfo.fadeIn()
    case _                => videoCallInfo.fadeOut()
  }

  val shouldShowInfo: Signal[Boolean]
}

class SelfVideoView(context: Context, participant: Participant)
  extends UserVideoView(context, participant) with DerivedLogTag {

    controller.isMuted.onUi {
        case true => audioStatusImageView.setImageResource(R.drawable.ic_muted_video_grid)
        case false => audioStatusImageView.setImageResource(R.drawable.ic_unmuted_video_grid)
      }

  controller.videoSendState.filter(_ == VideoState.Started).head.foreach { _ =>
    registerHandler(returning(new VideoPreview(getContext)) { v =>
      controller.setVideoPreview(Some(v))
      v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
      addView(v, 1)
    })
  }(Threading.Ui)

  override lazy val shouldShowInfo = Signal.zip(pausedTextVisible, controller.isMuted).map {
    case (paused, muted) => paused || muted
  }
}

class OtherVideoView(context: Context, participant: Participant) extends UserVideoView(context, participant) {

  participantInfo.onUi {
    case Some(info) =>
      if (info.isMuted) audioStatusImageView.setImageResource(R.drawable.ic_muted_video_grid)
      else audioStatusImageView.setImageResource(R.drawable.ic_unmuted_video_grid)
    case _ =>
  }

  override lazy val shouldShowInfo = pausedTextVisible
  registerHandler(returning(new VideoRenderer(getContext, participant.userId.str, participant.clientId.str, false)) { v =>
    v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addView(v, 1)
  })
}

class CallingFragment extends FragmentHelper {

  private val maxVideoPreviews = 12
  private lazy val controller       = inject[CallController]
  private lazy val themeController  = inject[ThemeController]
  private lazy val controlsFragment = ControlsFragment.newInstance
  private lazy val previewCardView  = view[CardView](R.id.preview_card_view)

  private var viewMap = Map[Participant, UserVideoView]()

  private lazy val videoGrid = returning(view[GridLayout](R.id.video_grid)) { vh =>
    Signal.zip(
      controller.allVideoReceiveStates,
      controller.callingZms.map(zms => Participant(zms.selfUserId, zms.clientId)),
      controller.isVideoCall,
      controller.isCallIncoming,
      controller.participantInfos()
    ).onUi { case (vrs, selfParticipant, videoCall, incoming, infos) =>

        def createView(participant: Participant): UserVideoView = returning {
          if (participant == selfParticipant) new SelfVideoView(getContext, participant)
          else new OtherVideoView(getContext, participant)
        } { v =>
          viewMap = viewMap.updated(participant, v)
        }

        def findParticipantNameById(userId: UserId): String = infos.find(_.userId == userId).get.displayName

        val isVideoBeingSent = !vrs.get(selfParticipant).contains(VideoState.Stopped)

      vh.foreach { v =>
        val videoUsers = vrs.toSeq.collect {
          case (participant, _) if participant == selfParticipant && videoCall && incoming => participant
          case (participant, VideoState.Started | VideoState.Paused | VideoState.BadConnection | VideoState.NoCameraPermission | VideoState.ScreenShare) => participant
        }
        val views = videoUsers.map { participant => viewMap.getOrElse(participant, createView(participant)) }

        viewMap.get(selfParticipant).foreach { selfView =>
          previewCardView.foreach { cardView =>
            if (views.size == 2 && isVideoBeingSent) {
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
          case _: SelfVideoView if views.size == 2 && isVideoBeingSent => false
          case _: SelfVideoView if views.size > 1 && !isVideoBeingSent => false
          case _ => true
        }.sortWith {
          case (_: SelfVideoView, _) => true
          case (_, _: SelfVideoView) => false
          case (v1, v2) => findParticipantNameById(v1.participant.userId).toLowerCase <
            findParticipantNameById(v2.participant.userId).toLowerCase
        }.take(maxVideoPreviews)

        gridViews.zipWithIndex.foreach { case (r, index) =>
          val (row, col, span, width) = index match {
            case 0 if !isVideoBeingSent && gridViews.size == 2 => (0, 0, 2, 0)
            case 0 => (0, 0, 1, 0)
            case 1 if !isVideoBeingSent && gridViews.size == 2 => (1, 0, 2, 0)
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

          r.setLayoutParams(returning(new GridLayout.LayoutParams()) { params =>
            params.width = width
            params.height = 0
            params.rowSpec = GridLayout.spec(row, 1, GridLayout.FILL, 1f)
            params.columnSpec = GridLayout.spec(col, span, columnAlignment,1f)
          })

          if (r.getParent == null) v.addView(r)
        }

        val viewsToRemove = viewMap.filter {
          case (participant, selfView) if participant == selfParticipant => !gridViews.contains(selfView)
          case (participant, _)                                          => !videoUsers.contains(participant)
        }
        viewsToRemove.foreach { case (_, view) => v.removeView(view) }
        viewMap = viewMap.filter { case (participant, _) => videoUsers.contains(participant) }
      }
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    controller.theme.map(themeController.getTheme).onUi(theme => videoGrid.foreach(_.setBackgroundColor(getStyledColor(R.attr.wireBackgroundColor, theme))))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    returning(inflater.inflate(R.layout.fragment_calling, container, false)) { v =>
      controller.theme(t => v.asInstanceOf[ThemeControllingFrameLayout].theme ! Some(t))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)

    videoGrid

    getChildFragmentManager
      .beginTransaction
      .replace(R.id.controls_layout, controlsFragment, ControlsFragment.Tag)
      .commit
  }

  override def onBackPressed() = {
    withChildFragmentOpt(R.id.controls_layout) {
      case Some(f: FragmentHelper) if f.onBackPressed()               => true
      case Some(_) if getChildFragmentManager.popBackStackImmediate() => true
      case _ => super.onBackPressed()
    }
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    viewMap = Map()
  }
}

object CallingFragment {
  val Tag: String = getClass.getSimpleName
  def apply(): CallingFragment = new CallingFragment()
}
