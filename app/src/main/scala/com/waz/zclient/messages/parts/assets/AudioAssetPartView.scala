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
package com.waz.zclient.messages.parts.assets

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.{FrameLayout, SeekBar}
import com.waz.service.assets.AssetStatus
import com.waz.service.assets.Asset.{Audio, Video}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.R
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.messages.{HighlightViewPart, MsgPart}
import com.waz.zclient.utils.{RichSeekBar, RichView, StringUtils}
import org.threeten.bp.Duration

class AudioAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with PlayableAsset with FileLayoutAssetPart with HighlightViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.AudioAsset

  private val progressBar: SeekBar = findById(R.id.progress)

  accentColorController.accentColor.map(_.color).onUi(progressBar.setColor)

  private val details = asset.map(_.details)
  private val duration = details.map {
    case d: Video => d.duration.toMillis.toInt
    case d: Audio => d.duration.toMillis.toInt
    case _        => 0
  }

  duration.onUi(progressBar.setMax)

  private val readyToPlay = details.map {
    case _: Video => true
    case _: Audio => true
    case _        => false
  }

  private val progressInMillis = for {
    ready     <- readyToPlay
    progress  <- if (ready) playControls.flatMap(_.playHead).map(_.toMillis.toInt)
                 else Signal.const(0)
  } yield progress

  progressInMillis.onUi(progressBar.setProgress)

  (for {
    ready         <- readyToPlay
    duration      <- duration
    isPlaying     <- playControls.flatMap(_.isPlaying)
    progress      <- progressInMillis
    displayedTime =  if (isPlaying || progress > 0) progress else duration
    formatted     =  if (ready) StringUtils.formatTimeMilliSeconds(displayedTime) else ""
  } yield formatted).onUi(durationView.setText)

  private lazy val keyboard = inject[CursorController].keyboard

  assetActionButton.onClick {
    assetStatus.map(_._1).currentValue match {
      case Some(AssetStatus.Done) =>
        keyboard ! KeyboardState.Hidden
        playControls.head.foreach(_.playOrPause())(Threading.Background)
      case _ =>
    }
  }

  completed.on(Threading.Ui) { progressBar.setEnabled }

  progressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
    override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit =
      if (fromUser) playControls.currentValue.foreach(_.setPlayHead(Duration.ofMillis(progress)))

    override def onStopTrackingTouch(seekBar: SeekBar): Unit = ()

    override def onStartTrackingTouch(seekBar: SeekBar): Unit = ()
  })
}
