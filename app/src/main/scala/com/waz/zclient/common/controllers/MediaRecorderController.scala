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

import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{Injectable, Injector, KotlinServices}
import com.waz.zclient.log.LogUI._
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.internal.functions.Functions
import com.waz.zclient.utils.ScalaToKotlin._

import scala.util.{Failure, Try}

trait MediaRecorderController {
  def startRecording(onFinish: File => Unit = _ => {}): Unit
  def stopRecording(): Try[Unit]
  def cancelRecording(): Unit
  def isRecording: Boolean

  def duration: Option[Long]
}

class MediaRecorderControllerImpl(context: Context)(implicit injector: Injector) extends MediaRecorderController with Injectable with DerivedLogTag {

  private lazy val m4aFile = new File(context.getCacheDir, "record_temp.m4a")

  private var recordingDisposable  = Option.empty[Disposable]
  private var startRecordingOffset = Option.empty[Long]
  private var recordingDuration    = Option.empty[Long]

  private lazy val audioService = KotlinServices.INSTANCE.getAudioService

  override def duration: Option[Long] = recordingDuration

  override def startRecording(onFinish: File => Unit = _ => {}): Unit = {
    verbose(l"startRecording")
    cancelRecording()

    if (m4aFile.exists()) m4aFile.delete()
    m4aFile.createNewFile()

    startRecordingOffset = Option(System.currentTimeMillis)
    recordingDuration = None

    recordingDisposable = Option(
      audioService.recordM4AAudio(m4aFile, { file: File =>
        recordingDuration = startRecordingOffset.map(System.currentTimeMillis - _)
        onFinish(file)
        recordingDisposable = None
      })
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          Functions.emptyConsumer(),
          { t: Throwable =>
            error(l"Recording failed", t)
            reset()
          }
        )
    )
  }

  override def stopRecording(): Try[Unit] = recordingDisposable match {
    case Some(r) =>
      verbose(l"stopRecording")
      Try(r.dispose()).recoverWith {
        case e: RuntimeException =>
          error(l"Failed to stop recorder properly: ${showString(e.getMessage)}", e)
          reset()
          Failure(e)
      }
    case None =>
      reset()
      error(l"Recording failed")
      Failure(new IllegalStateException("Recording failed"))
  }

  override def cancelRecording(): Unit = {
    verbose(l"cancelRecording")
    recordingDisposable.foreach(_.dispose())
    reset()
  }

  override def isRecording: Boolean = recordingDisposable.isDefined

  private def reset(): Unit = {
    if (m4aFile.exists()) m4aFile.delete()
    recordingDuration = None
    recordingDisposable = None
    startRecordingOffset = None
  }
}
