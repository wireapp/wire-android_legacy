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
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.{HighlightViewPart, MsgPart}
import com.waz.zclient.utils.RichSeekBar
import org.threeten.bp.Duration
import com.waz.ZLog.ImplicitTag._
import com.waz.service.assets2.AssetStatus
import com.waz.zclient.utils.RichView

class AudioAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with PlayableAsset with FileLayoutAssetPart with HighlightViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.AudioAsset

  private val progressBar: SeekBar = findById(R.id.progress)

  accentColorController.accentColor.map(_.color).onUi(progressBar.setColor)

  val playControls = controller.getPlaybackControls(asset)

  duration.map(_.getOrElse(Duration.ZERO).toMillis.toInt).on(Threading.Ui)(progressBar.setMax)
  playControls.flatMap(_.playHead).map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setProgress)
  playControls.flatMap(_.isPlaying) (isPlaying ! _)

  assetActionButton.onClick {
    assetStatus.map(_._1).currentValue match {
      case Some(AssetStatus.Done) =>
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
