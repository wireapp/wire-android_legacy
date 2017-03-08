/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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

import java.util.concurrent.TimeUnit

import android.content.Context
import android.util.AttributeSet
import android.view.View.{GONE, VISIBLE}
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view._
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.ZLog._
import com.waz.api.VideoSendState
import com.waz.avs.{VideoPreview, VideoRenderer}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CurrentCallController
import com.waz.zclient.ui.calling.{CallControlCameraToggleButtonView, RoundedLayout}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{R, ViewHelper}
import timber.log.Timber

import scala.concurrent.duration.FiniteDuration

class VideoCallingView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import Threading.Implicits.Ui
  import VideoCallingView._
  import com.waz.zclient.utils.RichView

  lazy val overlayView: View = findById(R.id.video_background_overlay)

  lazy val headerView: HeaderLayoutVideo = findById(R.id.header_video)
  lazy val messageView: TextView = findById(R.id.video_warning_message)

  lazy val selfViewLayout: LinearLayout = findById(R.id.self_view_layout)
  lazy val roundedLayout: RoundedLayout = findById(R.id.rounded_layout)

  lazy val selfPreviewPlaceHolder: View = findById(R.id.self_preview_place_holder)

  lazy val cameraToggleButton: CallControlCameraToggleButtonView = findById(R.id.camera_toggle_button)

  lazy val callingControls: ControlsView = findById(R.id.video_controls_layout)

  lazy val videoPreview = new VideoPreview(context)
  lazy val videoView = new VideoRenderer(context, false)

  var hasFullScreenBeenSet = false //need to make sure we don't set the FullScreen preview on call tear down! never gets set back to false
  var tapFuture: CancellableFuture[Unit] = _
  var isCameraToggleButtonVisible = false
  var isCallEstablished = false
  var inOrFadingIn = false

  val controller = inject[CurrentCallController]

  LayoutInflater.from(context).inflate(R.layout.calling_video, this, true)

  setId(R.id.video_calling_view) //for QA's automation tests

  this.onClick(toggleControlVisibility())

  cameraToggleButton.onClick {
    controller.currentCaptureDeviceIndex.mutate(_ + 1)
    extendControlsDisplay()
  }

  callingControls.onClickEvent.on(Threading.Ui)(_ => extendControlsDisplay())

  controller.glob.activeCall.map {
    case true => Some(videoPreview)
    case false => None
  }.on(Threading.Ui)(controller.setVideoPreview)

  controller.callEstablished.map {
    case true => Some(videoView)
    case false => None
  }.on(Threading.Ui)(controller.setVideoView)

  Signal(controller.glob.activeCall, controller.callEstablished).on(Threading.Ui) {
    case (true, false) if !hasFullScreenBeenSet =>
      Timber.d("Attaching videoPreview to fullScreen (call active, but not established")
      setFullScreenView(videoPreview)
      hasFullScreenBeenSet = true
    case (true, true) =>
      Timber.d("Attaching videoView to fullScreen and videoPreview to round layout, call active and established")
      setSmallPreview(videoPreview)
      setFullScreenView(videoView)
      extendControlsDisplay()
      hasFullScreenBeenSet = true //for the rare case the first match never fires
    case _ =>
  }

  controller.callEstablished.on(Threading.Ui)(selfViewLayout.setVisible)

  controller.callEstablished.on(Threading.Ui)(est => headerView.setVisible(!est))

  controller.callEstablished(isCallEstablished = _)

  controller.stateMessageText.on(Threading.Ui) {
    case (Some(message)) =>
      messageView.setVisibility(VISIBLE)
      messageView.setText(message)
    case _ =>
      messageView.setVisibility(GONE)
  }

  Signal(controller.callEstablished, controller.cameraFailed, controller.videoSendState).map {
    case (true, false, VideoSendState.SEND) => GONE
    case _ => VISIBLE
  }.on(Threading.Ui)(selfPreviewPlaceHolder.setVisibility)

  Signal(controller.callEstablished, controller.cameraFailed, controller.videoSendState).map {
    case (true, false, VideoSendState.SEND) => VISIBLE
    case _ => GONE
  }.on(Threading.Ui)(setSelfPreviewVisible)

  Signal(controller.callEstablished, controller.cameraFailed, controller.videoSendState, controller.captureDevices).map {
    case (true, false, VideoSendState.SEND, devices) if devices.size > 1 =>
      isCameraToggleButtonVisible = true
      VISIBLE
    case _ =>
      isCameraToggleButtonVisible = false
      GONE
  }.on(Threading.Ui)(cameraToggleButton.setVisibility)

  controller.currentCaptureDeviceIndex.on(Threading.Ui)(camIndex => cameraToggleButton.setFlipped(camIndex % 2 == 0))

  private def toggleControlVisibility(): Unit = {
    if (inOrFadingIn) {
      fadeOutControls()
    } else {
      fadeInControls()
      extendControlsDisplay()
    }
  }

  private def extendControlsDisplay(): Unit = if (isCallEstablished) {
    Option(tapFuture).foreach(_.cancel())
    tapFuture = CancellableFuture.delay(tapDelay)
    tapFuture.onSuccess {
      case _ => fadeOutControls()
    }
  }

  private def fadeInControls(): Unit = {
    ViewUtils.fadeInView(overlayView)
    if (isCameraToggleButtonVisible) {
      ViewUtils.fadeInView(cameraToggleButton)
    }
    ViewUtils.fadeInView(callingControls)
    inOrFadingIn = true
  }

  private def fadeOutControls(): Unit = {
    ViewUtils.fadeOutView(overlayView)
    ViewUtils.fadeOutView(cameraToggleButton)
    ViewUtils.fadeOutView(callingControls)
    inOrFadingIn = false
  }

  private def setSmallPreview(view: TextureView) = addVideoViewToLayout(roundedLayout, view)

  private def setFullScreenView(view: TextureView) = addVideoViewToLayout(this, view)

  private def setSelfPreviewVisible(visibility: Int) = {
    findVideoView(roundedLayout).foreach(_.setVisibility(visibility))
  }

  /**
    * Ensures there's only ever one video TextureView in a layout, and that it's always at the bottom. Both this layout
    * and the RoundedLayout for the small self-preview extend FrameLayout, so hopefully enforcing FrameLayout here
    * should break early if anything changes.
    */
  private def addVideoViewToLayout(layout: FrameLayout, videoView: View) = {
    removeVideoViewFromParent(videoView) //in case the videoView belongs to another parent
    removeVideoViewFromLayoutByTag(layout) //in case the layout has another videoView

    videoView.setTag(videoViewTag)
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
      if (t == videoViewTag)
    } yield v
}

object VideoCallingView {
  private val videoViewTag = "VIDEO_VIEW_TAG"
  private val tapDelay = FiniteDuration(3000, TimeUnit.MILLISECONDS)
  private implicit val tag: LogTag = logTagFor[VideoCallingView]
}

