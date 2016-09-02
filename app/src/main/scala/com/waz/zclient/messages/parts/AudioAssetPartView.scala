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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.{FrameLayout, SeekBar, TextView}
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.utils.{RichSeekBar, RichView}
import org.threeten.bp.Duration

class AudioAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with PlayableAsset {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.AudioAsset

  override def inflate() = inflate(R.layout.message_audio_asset_content)

  private val durationView: TextView = findById(R.id.duration)
  private val progressBar: SeekBar = findById(R.id.progress)

  val accentColorController = inject[AccentColorController]

  accentColorController.accentColor.map(_.getColor).on(Threading.Ui)(progressBar.setColor)

  val playControls = assets.getPlaybackControls(asset.map(_._1))

  formattedDuration.on(Threading.Ui)(durationView.setText)
  duration.map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setMax)
  playControls.flatMap(_.playHead).map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setProgress)

  actionReady.on(Threading.Ui) {
    case true =>
      progressBar.setEnabled(true)
      assetActionButton.listenToPlayState(playControls.flatMap(_.isPlaying))
      assetActionButton.onClick {
        playControls.currentValue.foreach(_.playOrPause())
      }

      progressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
        override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit =
          if (fromUser) playControls.currentValue.foreach(_.setPlayHead(Duration.ofMillis(progress)))

        override def onStopTrackingTouch(seekBar: SeekBar): Unit = ()

        override def onStartTrackingTouch(seekBar: SeekBar): Unit = ()
      })

    case false => progressBar.setEnabled(false)
  }

}
