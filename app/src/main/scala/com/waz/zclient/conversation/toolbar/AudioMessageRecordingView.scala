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

import android.Manifest.permission.RECORD_AUDIO
import android.animation.{ObjectAnimator, ValueAnimator}
import android.content.{Context, DialogInterface}
import android.graphics.LightingColorFilter
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View.{GONE, INVISIBLE, VISIBLE}
import android.view.{LayoutInflater, MotionEvent, View}
import android.widget.{FrameLayout, SeekBar, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.{AudioAssetForUpload, MessageContent, NetworkMode, PlaybackControls}
import com.waz.model.AssetId
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, RecordingCancelled, RecordingSuccessful}
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.Threading
import com.waz.utils.events.{ClockSignal, Signal}
import com.waz.utils.{RichThreetenBPDuration, returning}
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{SoundController, ThemeController}
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.api.scala.ModelObserver
import com.waz.zclient.ui.utils.CursorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StringUtils, ViewUtils}
import org.threeten.bp.Duration.between
import org.threeten.bp.Instant.now
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class AudioMessageRecordingView (val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)
  import AudioMessageRecordingView._
  import Threading.Implicits.Background

  LayoutInflater.from(getContext).inflate(R.layout.audio_quick_record_controls, this, true)

  lazy val recordingService = ZMessaging.currentGlobal.recordingAndPlayback
  lazy val permissions      = inject[PermissionsService]
  lazy val layoutController = inject[IGlobalLayoutController]
  lazy val convController   = inject[ConversationController]

  private val slideControlState = Signal(Recording)

  private var currentAsset    = Option.empty[AudioAssetForUpload]
  private var currentAssetKey = Option.empty[AssetMediaKey]
  private val startTime = Signal(Option.empty[Instant])

  //TODO remove playback controls
  private var playbackControls = Option.empty[PlaybackControls]
  private val playbackControlsModelObserver = new ModelObserver[PlaybackControls]() {
    override def updated(model: PlaybackControls) = {
      if (model.isPlaying) bottomButtonTextView.setText(R.string.glyph__pause)
      else bottomButtonTextView.setText(R.string.glyph__play)
      recordingSeekBar.setMax(model.getDuration.toMillis.toInt)
      recordingSeekBar.setProgress(model.getPlayhead.toMillis.toInt)
    }
  }

  val actionUpMinY = getDimenPx(R.dimen.audio_message_recording__slide_control__height) - 2 *
    getDimenPx(R.dimen.audio_message_recording__slide_control__width) - getDimenPx(R.dimen.wire__padding__8)

  private val closeButtonContainer             = findById[View]    (R.id.close_button_container)
  private val hintTextView                     = findById[TextView](R.id.controls_hint)
  private val recordingIndicatorDotView        = findById[View]    (R.id.recording_indicator_dot)
  private val recordingIndicatorContainerView  = findById[View]    (R.id.recording_indicator_container)
  private val bottomButtonTextView             = findById[TextView](R.id.bottom_button)
  private val sendButtonTextView               = findById[TextView](R.id.send_button)
  private val slideControl                     = findById[View]    (R.id.slide_control)
  private val timerTextView                    = findById[TextView](R.id.recording__duration)

  returning(findById[View](R.id.bottom_button_container))(_.onClick {
    playbackControls.foreach {
      case ctrls if ctrls.isPlaying => ctrls.stop()
      case ctrls => ctrls.play()
    }
  })

  returning(findById[View](R.id.send_button_container))(_.onClick {
    playbackControls.filter(_.isPlaying).foreach(_.stop())
    currentAsset.foreach(sendAudioAsset)
  })

  private val cancelButton = returning(findById[View](R.id.cancel_button_container))(_.onClick {
    if (!slideControlState.currentValue.contains(Recording)) {
      playbackControls.filter(_.isPlaying).foreach(_.stop())
      hide()
    }
  })

  private val recordingSeekBar = returning(findById[SeekBar](R.id.recording__seekbar)) { v =>
    v.setVisibility(GONE)
    v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) =
        playbackControls.filter(_ => fromUser).foreach(_.setPlayhead(Duration.ofMillis(progress)))

      override def onStartTrackingTouch(seekBar: SeekBar) = {}
      override def onStopTrackingTouch(seekBar: SeekBar) = {}
    })
  }

  inject[AccentColorController].accentColor.map(_.getColor).onUi { color =>
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

  slideControlState.map {
    case Recording => Some(R.string.glyph__microphone_on)
    case Preview   => Some(R.string.glyph__play)
    case _         => None
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
      startRecordingIndicator()
      layoutController.keepScreenAwake()
    case Preview   =>
      stopRecordingIndicator()
      startTime ! None
    case _ =>
  }

  startTime.flatMap[Option[Duration]] {
    case Some(start) =>
      ClockSignal(Duration.ofSeconds(1).asScala).map(_ => Some(between(start, now)))
    case _ => Signal.const(None)
  }.map {
    case Some(d) => StringUtils.formatTimeMilliSeconds(d.toMillis)
    case _       => ""
  }.onUi(timerTextView.setText)

  def hide() = {
    setVisibility(INVISIBLE)
    playbackControlsModelObserver.clear()
    playbackControls = None
    currentAssetKey.foreach(recordingService.cancelRecording)
    currentAssetKey = None
    currentAsset = None
    startTime ! None
    stopRecordingIndicator()
    layoutController.resetScreenAwakeState()
    slideControlState ! Recording //resets view state
  }

  def show() = {
    permissions.permissions(Set(RECORD_AUDIO)).map(_.headOption.exists(_.granted)).head.map {
      case true =>
        setVisibility(VISIBLE)
        slideControlState ! Recording
        inject[SoundController].shortVibrate()
        record()
      case false =>
        permissions.requestAllPermissions(Set(RECORD_AUDIO)).map {
          case false =>
            showToast(R.string.audio_message_error__missing_audio_permissions)
          case _ =>
        } (Threading.Ui)
    } (Threading.Ui)
  }

  private def record() = {
      val key = AssetMediaKey(AssetId())
      currentAssetKey = Some(key)
      recordingService.record(key, 25.minutes).flatMap { case (start, futureAsset) =>
        startTime ! Some(start)
        futureAsset.map {
          case RecordingSuccessful(asset, lengthLimitReached) =>
            if (lengthLimitReached) {
              ViewUtils.showAlertDialog(
                getContext,
                R.string.audio_message__recording__limit_reached__title,
                R.string.audio_message__recording__limit_reached__message,
                R.string.audio_message__recording__limit_reached__confirmation,
                R.string.confirmation_menu__cancel,
                new DialogInterface.OnClickListener() {
                  override def onClick(dialogInterface: DialogInterface, i: Int) = sendAudioAsset(asset)
                },
                null)
            } else slideControlState.currentValue match {
              case Some(SendFromRecording) =>
                sendAudioAsset(asset)
              case _ =>
                currentAsset = Some(asset)
                playbackControls = Some(returning(asset.getPlaybackControls) { ctrls =>
                  playbackControlsModelObserver.setAndUpdate(ctrls)
                })
            }
          case RecordingCancelled => throw new CancelException("Recording cancelled")
        } (Threading.Ui)
      }(Threading.Ui).onFailure {
        case NonFatal(_) =>
          playbackControlsModelObserver.clear()
          playbackControls = None
      } (Threading.Ui)
  }

  private def sendAudioAsset(asset: AudioAssetForUpload) = {
    (asset match {
      case asset: com.waz.api.impl.AudioAssetForUpload =>
        convController.currentConvId.head.flatMap { convId =>
          convController.sendMessage(convId, asset, new MessageContent.Asset.ErrorHandler() {
            override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = answer.ok()
          })
        }
      case _ => Future.successful({})
    }).map(_ => hide())(Threading.Ui)
  }

  def onMotionEventFromAudioMessageButton(motionEvent: MotionEvent) = {
    motionEvent.getAction match {
      case MotionEvent.ACTION_MOVE =>
        if (slidUpToSend(motionEvent)) slideControlState ! SendFromRecording
        else slideControlState ! Recording
      case MotionEvent.ACTION_CANCEL |
           MotionEvent.ACTION_OUTSIDE |
           MotionEvent.ACTION_UP =>
        currentAssetKey.foreach { key =>
          slideControlState.mutate {
            case Recording => Preview
            case st => st
          }
          recordingService.stopRecording(key)
        }
      case _ => //
    }
  }

  private def slidUpToSend(motionEvent: MotionEvent) = slideControlState.currentValue match {
    case Some(Recording) | Some(SendFromRecording) => motionEvent.getY <= actionUpMinY
    case _ => false
  }

  private var recordingIndicatorDotAnimator = Option.empty[ObjectAnimator]

  private def startRecordingIndicator() = {
    if (recordingIndicatorDotAnimator.isEmpty) {
      recordingIndicatorDotAnimator = Some(returning(ObjectAnimator.ofFloat(recordingIndicatorDotView, View.ALPHA, 0f)) { an =>
        an.setRepeatCount(ValueAnimator.INFINITE)
        an.setRepeatMode(ValueAnimator.REVERSE)
        an.setDuration(RecordingIndicatorHiddenInterval)
        an.setStartDelay(RecordingIndicatorVisibleInterval)
      })
    }
    recordingIndicatorDotAnimator.foreach(_.start())
  }

  private def stopRecordingIndicator() = recordingIndicatorDotAnimator.foreach(_.cancel())

  override protected def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = {
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
