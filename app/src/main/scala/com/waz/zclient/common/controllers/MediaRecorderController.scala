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
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.utils.returning
import com.waz.zclient.utils.media.{AudioEncoder, AudioSource, OutputFormat}

trait MediaRecorderController {
  def startRecording(): Unit
  def stopRecording(): Unit
  def cancelRecording(): Unit
  def isRecording: Boolean

  def getFile: File
}

class MediaRecorderControllerImpl(context: Context) extends MediaRecorderController {

  var recorder = Option.empty[MediaRecorder]
  lazy val file = new File(context.getCacheDir, "record_temp.mp4")

  private def getRecorder(file: File): MediaRecorder = returning(new MediaRecorder()) { r =>
      r.setAudioSource(AudioSource.MIC)
      r.setOutputFormat(OutputFormat.MPEG_4)
      r.setAudioEncoder(AudioEncoder.HE_AAC)
      r.setOutputFile(file)
    }

  override def startRecording(): Unit = {

    cancelRecording()

    file.delete()
    file.createNewFile()

    val rec = getRecorder(file)

    try {
      rec.prepare()
    } catch {
      case e: Throwable =>
        verbose(s"Failed to prepare recorder ${e.getMessage}") //TODO: Abort?
    }

    rec.start()

    recorder = Some(rec)
  }

  override def stopRecording(): Unit = {
    recorder.foreach { r =>
      r.stop()
      r.release()
    }
    recorder = None
  }

  override def cancelRecording(): Unit = stopRecording()

  override def isRecording: Boolean = false //TODO: ???

  override def getFile: File = file
}
