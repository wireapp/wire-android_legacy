/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api.impl

import com.waz.api
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService.{Content, MediaKey}
import com.waz.ui.{SignalLoading, UiModule}
import com.wire.signals.Signal
import org.threeten.bp.Duration

class PlaybackControls(key: MediaKey, content: Content, durationSource: ZMessaging => Signal[Duration])(implicit ui: UiModule)
  extends api.PlaybackControls with UiObservable with SignalLoading {
  private var playing = false
  private var playhead = Duration.ZERO
  private var duration = Duration.ZERO

  addLoader(zms => Signal.zip(zms.global.recordingAndPlayback.isPlaying(key), zms.global.recordingAndPlayback.playhead(key), durationSource(zms))) {
    case (nextPlaying, nextPlayhead, nextDuration) =>
      if (nextPlaying != playing || nextPlayhead != playhead || nextDuration != duration) {
        playing = nextPlaying
        playhead = implicitly[Ordering[Duration]].min(nextPlayhead, nextDuration)
        duration = nextDuration
        notifyChanged()
      }
  }

  override def play: Unit = ui.global.recordingAndPlayback.play(key, content)
  override def stop: Unit = ui.global.recordingAndPlayback.pause(key)

  override def isPlaying: Boolean = playing
  override def getDuration: Duration = duration

  override def getPlayhead: Duration = playhead
  override def setPlayhead(ph: Duration): Unit = ui.global.recordingAndPlayback.setPlayhead(key, content, ph)

  override def toString: String = s"PlaybackControls($key, $content, playing = $playing, $playhead of $duration)"
}
