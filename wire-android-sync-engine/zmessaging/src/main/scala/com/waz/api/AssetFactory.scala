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
package com.waz.api

import com.waz.api.impl.{ContentUriAssetForUpload, RecordingLevels}
import com.waz.model.AssetId
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Audio, RecordingCancelled, RecordingSuccessful}
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AssetFactory {

  /**
    * Gets an asset from a URI. Only the "content" scheme is supported
    */
  def fromContentUri(uri: URI): AssetForUpload = {
    ContentUriAssetForUpload(AssetId(), uri)
  }

  def recordAudioAsset(react: RecordingCallback): RecordingControls = {
    implicit val ui = ZMessaging.currentUi
    val service = ZMessaging.currentGlobal.recordingAndPlayback

    val key = AssetMediaKey(AssetId())
    val levels = new RecordingLevels(service.recordingLevel(key))
    service.record(key, 25.minutes).onComplete {
      case Success((instantOfStart, futureAsset)) =>
        react.onStart(instantOfStart)
        futureAsset.onComplete {
          case Success(RecordingSuccessful(audio, lengthLimitReached)) =>
            react.onComplete(audio, lengthLimitReached, levels.overview)
          case Success(RecordingCancelled) | Failure(_) =>
            react.onCancel()
        }(Threading.Ui)
      case Failure(cause) =>
        react.onCancel()
    }(Threading.Ui)

    new RecordingControls {
      override def stop(): Unit = service.stopRecording(key)
      override def cancel(): Unit = service.cancelRecording(key)
      override def soundLevels(windowSize: Int): UiSignal[Array[Float]] = impl.UiSignal(_ => levels.windowed(windowSize))
      override def finalize: Unit = cancel()
    }
  }
}

trait RecordingCallback {
  def onStart(timestamp: Instant): Unit
  def onComplete(audio: Audio, fileSizeLimitReached: Boolean, overview: AudioOverview): Unit
  def onCancel(): Unit
}

trait RecordingControls {
  def stop(): Unit
  def cancel(): Unit

  def soundLevels(numberOfLevels: Int): UiSignal[Array[Float]] // single entry ∈ (0, 1) represents the peak "loudness" of the signal over a period of time
}

trait AudioOverview {
  def getLevels(numberOfLevels: Int): Array[Float]
  def isEmpty: Boolean
}
