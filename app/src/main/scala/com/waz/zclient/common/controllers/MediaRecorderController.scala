/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.common.controllers

import java.io.File

import android.media.MediaRecorder
import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.utils.media.{AudioEncoder, AudioSource, OutputFormat}
import com.waz.zclient.log.LogUI._


trait MediaRecorderController {
  def startRecording(): Unit
  def stopRecording(): Boolean
  def cancelRecording(): Unit
  def isRecording: Boolean

  def getFile: File
  def duration: Option[Long]
}

class MediaRecorderControllerImpl(context: Context) extends MediaRecorderController with DerivedLogTag {

  private var recorder = Option.empty[MediaRecorder]
  private lazy val file = new File(context.getCacheDir, "record_temp.mp4")

  private var startRecordingOffset = Option.empty[Long]
  private var recordingDuration = Option.empty[Long]

  override def duration: Option[Long] = recordingDuration

  private def getRecorder(file: File): MediaRecorder = returning(new MediaRecorder()) { r =>
      r.setAudioSource(AudioSource.MIC)
      r.setOutputFormat(OutputFormat.MPEG_4)
      r.setAudioEncoder(AudioEncoder.HE_AAC)
      r.setOutputFile(file.getAbsolutePath)
    }

  override def startRecording(): Unit = {

    cancelRecording()

    if (file.exists()) file.delete()
    file.createNewFile()

    val rec = getRecorder(file)

    try {
      rec.prepare()
      rec.start()
    } catch {
      case e: Throwable =>
        verbose(l"Failed to prepare or start recorder: ${showString(e.getMessage)}")
    } finally {
      recorder = Some(rec)
      startRecordingOffset = Option(System.currentTimeMillis())
      recordingDuration = None
    }
  }

  override def stopRecording(): Boolean =
    recorder.fold(false){ r =>
      try {
        r.stop()
        recordingDuration = startRecordingOffset.map(System.currentTimeMillis() - _)
        true
      } catch {
        case e: RuntimeException =>
          verbose(l"Failed to stop recorder properly: ${showString(e.getMessage)}")
          file.delete()
          recordingDuration = None
          false
      } finally {
        r.release()
        recorder = None
        startRecordingOffset = None
      }
    }

  override def cancelRecording(): Unit = stopRecording()

  override def isRecording: Boolean = recorder.isDefined

  override def getFile: File = file
}
