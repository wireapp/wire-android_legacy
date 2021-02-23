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
package com.waz.zclient.conversation.toolbar

import java.io.File

import android.animation.{ObjectAnimator, ValueAnimator}
import android.app.Activity
import android.content.Context
import android.graphics.LightingColorFilter
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View.{GONE, INVISIBLE, VISIBLE}
import android.view.{LayoutInflater, MotionEvent, View, WindowManager}
import android.widget.{FrameLayout, SeekBar, TextView, Toast}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetId, Mime}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.{Content, ContentForUpload}
import com.waz.service.assets.GlobalRecordAndPlayService.AssetMediaKey
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.wrappers.URI
import com.waz.utils.{RichThreetenBPDuration, returning}
import com.waz.zclient.common.controllers.AssetsController.PlaybackControls
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{MediaRecorderController, SoundController, ThemeController}
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.utils.CursorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StringUtils}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Duration.between
import org.threeten.bp.Instant.now
import org.threeten.bp.{Duration, Instant}
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future
import com.waz.threading.Threading._
import com.wire.signals.ext.ClockSignal

class AudioMessageRecordingView (val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)
  import AudioMessageRecordingView._

  LayoutInflater.from(getContext).inflate(R.layout.audio_quick_record_controls, this, true)

  lazy val recordAndPlay = inject[Signal[ZMessaging]].map(_.global.recordingAndPlayback)
  lazy val recordingController = inject[MediaRecorderController]
  lazy val permissions      = inject[PermissionsService]
  lazy val layoutController = inject[IGlobalLayoutController]
  lazy val convController   = inject[ConversationController]

  private val slideControlState = Signal(Recording)

  private var currentAudio    = Option.empty[ContentForUpload]
  private var currentAssetKey = Option.empty[AssetMediaKey]
  private val startTime = Signal(Option.empty[Instant])

  private val playbackControls = Signal[PlaybackControls]()

  private val actionUpMinY = getDimenPx(R.dimen.audio_message_recording__slide_control__height) - 2 *
    getDimenPx(R.dimen.audio_message_recording__slide_control__width) - getDimenPx(R.dimen.wire__padding__8)

  private val closeButtonContainer             = findById[View]    (R.id.close_button_container)
  private val hintTextView                     = findById[TextView](R.id.controls_hint)
  private val recordingIndicatorDotView        = findById[View]    (R.id.recording_indicator_dot)
  private val recordingIndicatorContainerView  = findById[View]    (R.id.recording_indicator_container)
  private val bottomButtonTextView             = findById[TextView](R.id.bottom_button)
  private val sendButtonTextView               = findById[TextView](R.id.send_button)
  private val slideControl                     = findById[View]    (R.id.slide_control)
  private val timerTextView                    = findById[TextView](R.id.recording__duration)

  private lazy val recordingIndicator =
    returning(ObjectAnimator.ofFloat(recordingIndicatorDotView, View.ALPHA, 0f)) { an =>
      an.setRepeatCount(ValueAnimator.INFINITE)
      an.setRepeatMode(ValueAnimator.REVERSE)
      an.setDuration(RecordingIndicatorHiddenInterval)
      an.setStartDelay(RecordingIndicatorVisibleInterval)
    }

  returning(findById[View](R.id.bottom_button_container))(_.onClick {
    playbackControls.currentValue.foreach { _.playOrPause() }
  })

  returning(findById[View](R.id.send_button_container))(_.onClick {
    playbackControls.currentValue.foreach { pc =>
      if (pc.isPlaying.currentValue.contains(true)) pc.playOrPause()
    }
    currentAudio.foreach(sendAudioAsset)
  })

  private val cancelButton = returning(findById[View](R.id.cancel_button_container))(_.onClick {
    if (!slideControlState.currentValue.contains(Recording)) {
      playbackControls.currentValue.foreach { pc =>
        if (pc.isPlaying.currentValue.contains(true)) pc.playOrPause()
      }
      hide()
    }
  })

  private val recordingSeekBar = returning(findById[SeekBar](R.id.recording__seekbar)) { v =>
    v.setVisibility(GONE)
    v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = {
        if (fromUser) playbackControls.currentValue.foreach(_.setPlayHead(Duration.ofMillis(progress)))
      }

      override def onStartTrackingTouch(seekBar: SeekBar) = {}
      override def onStopTrackingTouch(seekBar: SeekBar) = {}
    })
  }

  inject[AccentColorController].accentColor.map(_.color).onUi { color =>
    Option(recordingSeekBar.getProgressDrawable  ).foreach { drawable =>
      val filter = new LightingColorFilter(0xFF000000, color)

      drawable match {
        case layerDrawable: LayerDrawable =>
          Option(layerDrawable.findDrawableByLayerId(android.R.id.progress)).foreach(_.setColorFilter(filter))
        case _ =>
          drawable.setColorFilter(filter)
      }
      Option(recordingSeekBar.getThumb).foreach(_.setColorFilter(filter))
    }
  }

  (for {
    darkTheme <- inject[ThemeController].darkThemePref.flatMap(_.signal)
    state     <- slideControlState
  } yield (state, darkTheme) match {
    case (SendFromRecording, _) |
         (_, true)              => R.color.wire__text_color_primary_dark_selector
    case _                      => R.color.wire__text_color_primary_light_selector
  }).map(getColor)
    .onUi(bottomButtonTextView.setTextColor)

  slideControlState.map {
    case SendFromRecording => R.color.wire__text_color_primary_dark_selector
    case _                 => R.color.accent_green
  }.map(getColor).onUi(sendButtonTextView.setTextColor)

  slideControlState.map {
    case SendFromRecording => R.drawable.audio_message__slide_control__background_accent__green
    case _                 => R.drawable.audio_message__slide_control__background
  }.map(getDrawable(_)).onUi(slideControl.setBackground)

  slideControlState.map {
    case Recording => Some(R.string.audio_message__recording__slide_control__slide_hint)
    case Preview   => Some(R.string.audio_message__recording__slide_control__tap_hint)
    case _         => None
  }.map(_.map(getString)).onUi(_.foreach(hintTextView.setText))

  Signal.zip(slideControlState, playbackControls.flatMap(_.isPlaying).orElse(Signal.const(false))).map {
    case (Recording, _)   => Some(R.string.glyph__microphone_on)
    case (Preview, false) => Some(R.string.glyph__play)
    case (Preview, true)  => Some(R.string.glyph__pause)
    case _                => None
  }.map(_.map(getString)).onUi(_.foreach(bottomButtonTextView.setText))

  slideControlState.map {
    case Recording => Some(VISIBLE)
    case Preview   => Some(GONE)
    case _         => None
  }.onUi(_.foreach(recordingIndicatorContainerView.setVisibility))

  slideControlState.map {
    case Recording => Some(GONE)
    case Preview   => Some(VISIBLE)
    case _         => None
  }.onUi(_.foreach(recordingSeekBar.setVisibility))

  slideControlState.onChanged.onUi {
    case Recording =>
      recordingIndicator.start()
      layoutController.keepScreenAwake()
    case Preview   =>
      recordingIndicator.cancel()
      startTime ! None
    case SendFromRecording =>
      recordingIndicator.cancel()
      currentAudio.foreach(sendAudioAsset)
    case _ =>
  }

  private val previewProgress = startTime.flatMap {
    case Some(start) =>
      ClockSignal(Duration.ofSeconds(1).asScala).map(_ => Some(between(start, now)))
    case _ => playbackControls.flatMap(_.playHead).map(Some(_))
  }

  (for {
    playing       <- playbackControls.flatMap(_.isPlaying).orElse(Signal.const(false))
    progress      <- previewProgress.map(_.map(_.toMillis))
    displayedTime =  if (playing || recordingController.isRecording || progress.exists(_ > 0L)) progress
                     else recordingController.duration
    formatted     =  displayedTime.fold("")(StringUtils.formatTimeMilliSeconds)
  } yield formatted).onUi(timerTextView.setText)

  previewProgress.onUi {
    case Some(d) => recordingSeekBar.setProgress(d.toMillis.toInt)
    case _ =>
  }

  def hide(): Unit = {
    setVisibility(INVISIBLE)
    recordingController.cancelRecording()
    currentAssetKey = None
    currentAudio = None
    startTime ! None
    recordingIndicator.cancel()
    layoutController.resetScreenAwakeState()
    slideControlState ! Recording //resets view state
  }

  def show(): Unit = {
    def onFinishRecording(m4aFile: File): Unit = currentAssetKey.foreach { key =>
      verbose(l"recording succeeded")
      val content = ContentForUpload(s"recording-${System.currentTimeMillis}", Content.File(Mime.Audio.M4A, m4aFile))
      currentAudio = Some(content)
      playbackControls ! new PlaybackControls(key.id, URI.fromFile(m4aFile), recordAndPlay)
      recordingController.duration.foreach(d => recordingSeekBar.setMax(d.toInt))
    }

    setVisibility(VISIBLE)
    slideControlState ! Recording
    inject[SoundController].shortVibrate()
    currentAssetKey = Option(AssetMediaKey(AssetId()))
    setWakeLock(true)
    recordingController.startRecording(onFinishRecording)
    startTime ! Some(Instant.now)
    recordingIndicator.start()
  }

  private def setWakeLock(enabled: Boolean): Unit = {
    val activity: Activity = this.getContext.asInstanceOf[Activity]
    if(enabled) {
      activity.getWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      activity.getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private def sendAudioAsset(content: ContentForUpload): Future[Unit] =
    convController.sendAssetMessage(content, None).map(_ => hide())(Threading.Ui)

  def onMotionEventFromAudioMessageButton(motionEvent: MotionEvent): Unit = {
    def stopRecording(): Unit = {
      setWakeLock(false)
      if (recordingController.stopRecording().isFailure) {
        Toast.makeText(getContext, getString(R.string.audio_message__recording__failure__title), Toast.LENGTH_SHORT).show()
        hide()
      }
    }

    motionEvent.getAction match {
      case _ if !recordingController.isRecording =>

      case MotionEvent.ACTION_MOVE if slidUpToSend(motionEvent) =>
        slideControlState ! SendFromRecording
        stopRecording()

      case MotionEvent.ACTION_CANCEL |
           MotionEvent.ACTION_OUTSIDE |
           MotionEvent.ACTION_UP =>
        stopRecording()
        slideControlState.mutate {
          case Recording => Preview
          case st        => st
        }

      case _ =>
    }
  }

  private def slidUpToSend(motionEvent: MotionEvent) = slideControlState.currentValue match {
    case Some(Recording) | Some(SendFromRecording) => motionEvent.getY <= actionUpMinY
    case _ => false
  }

  override protected def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    closeButtonContainer.setWidth(CursorUtils.getDistanceOfAudioMessageIconToLeftScreenEdge(getContext))
    cancelButton.setMarginLeft(CursorUtils.getMarginBetweenCursorButtons(getContext))
  }

}

object AudioMessageRecordingView {

  private type SlideControlState = Int
  private val Recording:         SlideControlState = 1
  private val SendFromRecording: SlideControlState = 2
  private val Preview:           SlideControlState = 3

  val RecordingIndicatorVisibleInterval = 750
  val RecordingIndicatorHiddenInterval = 350
}
