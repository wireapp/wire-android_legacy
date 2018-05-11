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
import android.widget.{FrameLayout, TextView}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.ZLog.verbose
import com.waz.api.VideoSendState
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.calling.views.{CallingHeader, CallingMiddleLayout, ControlsView}
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.duration.FiniteDuration

class ControlsFragment extends FragmentHelper {

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

  private lazy val overlayView = returning(view[View](R.id.video_background_overlay)) { vh =>
    controller.showVideoView.onUi { visible =>
      verbose(s"overlay visible: $visible")
      vh.foreach(_.setVisible(visible))
    }
  }

  private lazy val callingHeader = view[CallingHeader](R.id.calling_header)
  private lazy val callingMiddle = view[CallingMiddleLayout](R.id.calling_middle)
  private lazy val callingControls = view[ControlsView](R.id.controls_grid)

  private lazy val messageView = returning(view[TextView](R.id.video_warning_message)) { vh =>
    val startedAsVideo = controller.isVideoCall.currentValue.getOrElse(false)

    controller.stateMessageText.onUi {
      case (Some(message)) if startedAsVideo =>
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

  private lazy val videoView = new VideoRenderer(ctx, false)
  private lazy val videoPreview = new VideoPreview(ctx)

  private var hasFullScreenBeenSet = false //need to make sure we don't set the FullScreen preview on call tear down! never gets set back to false
  private var inOrFadingIn = false
  private var tapFuture: CancellableFuture[Unit] = _

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_calling_controls, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    getActivity.getWindow.setBackgroundDrawableResource(R.color.calling_background)

    controller.isCallActive.onUi {
      case false =>
        verbose("call no longer exists, finishing activity")
        getActivity.finish()
      case _ =>
    }

    controller.callConvId.onChanged.onUi(_ => restart())

    //ensure activity gets killed to allow content to change if the conv degrades (no need to kill activity on audio call)
    (for {
      degraded <- controller.convDegraded
      video    <- controller.isVideoCall
    } yield degraded && video).onChanged.filter(_ == true).onUi(_ => getActivity.finish())

    v.onClick(toggleControlVisibility())

    Signal(controller.showVideoView, controller.isCallActive, controller.isCallEstablished).onUi {
      case (true, true, false) if !hasFullScreenBeenSet =>
        verbose("Attaching videoPreview to fullScreen (call active, but not established)")
        videoPreview.setVisible(true)
        setFullScreenView(videoPreview)
        hasFullScreenBeenSet = true
      case (true, true, true) =>
        verbose("Attaching videoView to fullScreen and videoPreview to round layout, call active and established")
        videoView.setVisible(true)
        setFullScreenView(videoView)
        videoPreview.setVisible(true)
        callingHeader.foreach(_.setPreview(videoPreview))
        extendControlsDisplay()
        hasFullScreenBeenSet = true //for the rare case the first match never fires
      case _ =>
        verbose("hiding video view and preview")
        videoPreview.setVisible(false)
        videoView.setVisible(false)
    }

    controller.videoSendState.map {
      case VideoSendState.DONT_SEND => None
      case _                        => Some(videoPreview)
    }.onUi(controller.setVideoPreview)

    overlayView
    degradedWarningTextView
    degradedConfirmationTextView
    callingHeader
    callingMiddle
    callingControls
    messageView
    videoView
  }

  private var subs = Set[Subscription]()

  override def onStart(): Unit = {
    super.onStart()
    callingControls.foreach(controls =>
      subs += controls.onButtonClick.onUi(_ => extendControlsDisplay())
    )
  }

  override def onStop(): Unit = {
    subs.foreach(_.destroy())
    super.onStop()
  }

  private def restart() = {
    verbose("restart")
    getActivity.finish()
    CallingActivity.start(ctx)
    getActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
  }

  private def toggleControlVisibility(): Unit = if (controller.showVideoView.currentValue.getOrElse(false)) {
    if (inOrFadingIn) {
      fadeOutControls()
    } else {
      fadeInControls()
      extendControlsDisplay()
    }
  }

  private def extendControlsDisplay(): Unit = if (controller.showVideoView.currentValue.getOrElse(false)) {
    verbose(s"extendControlsDisplay")
    Option(tapFuture).foreach(_.cancel())
    tapFuture = CancellableFuture.delay(ControlsFragment.tapDelay)
    tapFuture.foreach { _ => fadeOutControls() }(Threading.Ui)
  }

  private def fadeInControls(): Unit = {
    verbose(s"fadeInControls")
    overlayView.foreach(ViewUtils.fadeInView)
    callingControls.foreach(ViewUtils.fadeInView)
    inOrFadingIn = true
  }

  private def fadeOutControls(): Unit = {
    verbose(s"fadeOutControls")
    overlayView.foreach(ViewUtils.fadeOutView)
    callingControls.foreach(ViewUtils.fadeOutView)
    inOrFadingIn = false
  }

  private def setFullScreenView(view: TextureView) = ControlsFragment.addVideoViewToLayout(getView.asInstanceOf[FrameLayout], view)
}

object ControlsFragment {
  private val videoViewTag = "VIDEO_VIEW_TAG"
  private val tapDelay = FiniteDuration(3000, TimeUnit.MILLISECONDS)
  val Tag = classOf[ControlsFragment].getName

  def newInstance: Fragment = new ControlsFragment

  /**
    * Ensures there's only ever one video TextureView in a layout, and that it's always at the bottom. Both this layout
    * and the RoundedLayout for the small self-preview extend FrameLayout, so hopefully enforcing FrameLayout here
    * should break early if anything changes.
    */
  def addVideoViewToLayout(layout: FrameLayout, videoView: View) = {
    removeVideoViewFromParent(videoView) //in case the videoView belongs to another parent
    removeVideoViewFromLayoutByTag(layout) //in case the layout has another videoView

    videoView.setTag(ControlsFragment.videoViewTag)
    layout.addView(videoView, 0, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
  }

  /**
    * Needed to remove a TextureView from its parent in case we try and set it as a child of a different layout
    * (the self-preview TextureView moves from fullscreen to the small layout when call is answered)
    */
  private def removeVideoViewFromParent(videoView: View): Unit =
    Option(videoView.getParent.asInstanceOf[ViewGroup]).foreach(_.removeView(videoView))

  private def removeVideoViewFromLayoutByTag(layout: ViewGroup): Unit =
    findVideoView(layout).foreach(layout.removeView)

  private def findVideoView(layout: ViewGroup) =
    for {
      v <- Option(layout.getChildAt(0))
      t <- Option(v.getTag) if t == ControlsFragment.videoViewTag
    } yield v
}
