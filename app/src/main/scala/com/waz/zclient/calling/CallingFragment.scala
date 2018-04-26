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

import java.util.concurrent.TimeUnit

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.Fragment
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view._
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.api.VideoSendState
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{ControlsView, HeaderLayoutAV}
import com.waz.zclient.ui.calling.RoundedLayout
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{BaseActivity, FragmentHelper, R}

import scala.concurrent.duration.FiniteDuration

class CallingFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity

  private lazy val controller = inject[CallController]

  private lazy val degradedWarningTextView = returning(view[TextView](R.id.degraded_warning)) { vh =>
    controller.convDegraded.onUi { degraded => vh.foreach(_.setVisible(degraded))}
    controller.degradationWarningText.onUi { text => vh.foreach(_.setText(text))}
  }

  private lazy val degradedConfirmationTextView = returning(view[TextView](R.id.degraded_confirmation)) { vh =>
    controller.convDegraded.onUi { degraded => vh.foreach(_.setVisible(degraded))}
    controller.degradationConfirmationText.onUi { text => vh.foreach(_.setText(text))}
  }

  private lazy val overlayView: View = findById(R.id.video_background_overlay)

  private lazy val headerView = returning(view[HeaderLayoutAV](R.id.header_layout)) { vh =>
    controller.isCallEstablished.onUi { est =>
      verbose(s"header visible: $est"); vh.foreach(_.setVisible(!est))
    }
  }

  private lazy val messageView = returning(view[TextView](R.id.video_warning_message)) { vh =>
    controller.stateMessageText.onUi {
      case (Some(message)) =>
        vh.foreach { messageView =>
          messageView.setVisible(true)
          messageView.setText(message)
          verbose(s"messageView text: $message")
        }
      case _ =>
        verbose("messageView gone")
        vh.foreach(_.setVisible(false))
    }
  }

  private lazy val selfViewLayout = returning(view[LinearLayout](R.id.self_view_layout)) { vh =>
    controller.isCallEstablished.onUi { est =>
      verbose(s"self view visible: $est"); vh.foreach(_.setVisible(est))
    }
  }

  private lazy val roundedLayout = returning(view[RoundedLayout](R.id.rounded_layout)) { vh =>
    Signal(controller.showVideoView, controller.isCallEstablished, controller.cameraFailed, controller.videoSendState).map {
      case (true, true, false, VideoSendState.SEND) => true
      case _                                  => false
    }.onUi { visible =>
      vh.foreach { view =>
        verbose(s"video view visible: $visible")
        view.setVisible(visible)
      }
    }
  }

  private lazy val selfPreviewPlaceHolder = returning(view[View](R.id.self_preview_place_holder)) { vh =>
    Signal(controller.showVideoView, controller.isCallEstablished, controller.cameraFailed, controller.videoSendState).map {
      case (true, _, _, _) => false
      case (_, true, false, VideoSendState.SEND) => false
      case _ => true
    }.onUi { visible => verbose(s"self preview placeHolder visible: $visible"); vh.foreach(_.setVisible(visible)) }
  }


  private lazy val callingControls = returning(view[ControlsView](R.id.controls_grid)) {
    _.foreach(_.onClickEvent.onUi(_ => extendControlsDisplay()))
  }

  private lazy val videoView = new VideoRenderer(ctx, false)
  private lazy val videoPreview = new VideoPreview(ctx)

  private var hasFullScreenBeenSet = false //need to make sure we don't set the FullScreen preview on call tear down! never gets set back to false
  private var inOrFadingIn = false
  private var isCallEstablished = false
  private var tapFuture: CancellableFuture[Unit] = _

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_calling, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    verbose(s"onViewCreated")

    getActivity.getWindow.setBackgroundDrawableResource(R.color.calling_background)

    controller.isCallActive.onUi {
      case false =>
        verbose("call no longer exists, finishing activity")
        getActivity.asInstanceOf[BaseActivity].finish()
      case _ =>
    }

    controller.callConvId.onChanged.onUi(_ => restart())

    //ensure activity gets killed to allow content to change if the conv degrades (no need to kill activity on audio call)
    (for {
      degraded <- controller.convDegraded
      video    <- controller.isVideoCall
    } yield degraded && video).onChanged.filter(_ == true).onUi(_ => getActivity.asInstanceOf[BaseActivity].finish())

    v.onClick(toggleControlVisibility())

    controller.isCallEstablished(isCallEstablished = _)

    controller.isCallEstablished.map {
      case true  => Some(videoView)
      case false => None
    }.onUi(controller.setVideoView)

    Signal(controller.showVideoView, controller.isCallActive, controller.isCallEstablished).onUi {
      case (true, true, false) if !hasFullScreenBeenSet =>
        verbose("Attaching videoPreview to fullScreen (call active, but not established)")
        setFullScreenView(videoPreview)
        hasFullScreenBeenSet = true
      case (true, true, true) =>
        verbose("Attaching videoView to fullScreen and videoPreview to round layout, call active and established")
        setFullScreenView(videoView)
        setSmallPreview(videoPreview)
        extendControlsDisplay()
        hasFullScreenBeenSet = true //for the rare case the first match never fires
      case _ =>
    }

    controller.videoSendState.map {
      case VideoSendState.DONT_SEND => None
      case _                        => Some(videoPreview)
    }.onUi(controller.setVideoPreview)

    overlayView
    degradedWarningTextView
    degradedConfirmationTextView
    headerView
    messageView
    selfViewLayout
    roundedLayout
    selfPreviewPlaceHolder
    callingControls
    videoView
  }

  override def onBackPressed(): Boolean = true

  private def restart() = {
    verbose("restart")
    getActivity.asInstanceOf[BaseActivity].finish()
    CallingActivity.start(ctx)
    getActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
  }

  private def toggleControlVisibility(): Unit = {
    if (inOrFadingIn) {
      fadeOutControls()
    } else {
      fadeInControls()
      extendControlsDisplay()
    }
  }

  private def extendControlsDisplay(): Unit = if (isCallEstablished) {
    verbose(s"extendControlsDisplay")
    Option(tapFuture).foreach(_.cancel())
    tapFuture = CancellableFuture.delay(CallingFragment.tapDelay)
    tapFuture.foreach { _ => fadeOutControls() }(Threading.Ui)
  }

  private def fadeInControls(): Unit = {
    verbose(s"fadeInControls")
    ViewUtils.fadeInView(overlayView)
    callingControls.foreach(ViewUtils.fadeInView)
    inOrFadingIn = true
  }

  private def fadeOutControls(): Unit = {
    verbose(s"fadeOutControls")
    ViewUtils.fadeOutView(overlayView)
    callingControls.foreach(ViewUtils.fadeOutView)
    inOrFadingIn = false
  }

  private def setSmallPreview(view: TextureView) = roundedLayout.foreach(addVideoViewToLayout(_, view))

  private def setFullScreenView(view: TextureView) = addVideoViewToLayout(getView.asInstanceOf[FrameLayout], view)

  /**
    * Ensures there's only ever one video TextureView in a layout, and that it's always at the bottom. Both this layout
    * and the RoundedLayout for the small self-preview extend FrameLayout, so hopefully enforcing FrameLayout here
    * should break early if anything changes.
    */
  private def addVideoViewToLayout(layout: FrameLayout, videoView: View) = {
    removeVideoViewFromParent(videoView) //in case the videoView belongs to another parent
    removeVideoViewFromLayoutByTag(layout) //in case the layout has another videoView

    videoView.setTag(CallingFragment.videoViewTag)
    layout.addView(videoView, 0, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
  }

  /**
    * Needed to remove a TextureView from its parent in case we try and set it as a child of a different layout
    * (the self-preview TextureView moves from fullscreen to the small layout when call is answered)
    */
  private def removeVideoViewFromParent(videoView: View): Unit = {
    val layout = Option(videoView.getParent.asInstanceOf[ViewGroup])
    layout.foreach(_.removeView(videoView))
  }

  private def removeVideoViewFromLayoutByTag(layout: ViewGroup): Unit = {
    findVideoView(layout).foreach(layout.removeView)
  }

  private def findVideoView(layout: ViewGroup) =
    for {
      v <- Option(layout.getChildAt(0))
      t <- Option(v.getTag)
      if (t == CallingFragment.videoViewTag)
    } yield v
}

object CallingFragment {
  private val videoViewTag = "VIDEO_VIEW_TAG"
  private val tapDelay = FiniteDuration(3000, TimeUnit.MILLISECONDS)
  val Tag = classOf[CallingFragment].getName

  def newInstance: Fragment = new CallingFragment
}
