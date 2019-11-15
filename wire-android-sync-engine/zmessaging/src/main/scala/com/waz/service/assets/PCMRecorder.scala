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
package com.waz.service.assets

import java.io.File
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.{ByteBuffer, ShortBuffer}
import java.util.concurrent.atomic.AtomicReference

import android.media.AudioRecord
import android.media.AudioRecord._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.log.LogShow.SafeToLog
import com.waz.threading.Threading
import com.waz.threading.Threading.IO
import com.waz.utils._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.math.{abs, max}
import scala.util.{Failure, Success}

trait PCMRecorder {
  def stopRecording(): Future[PCMRecorder.CompletionCause]
  def cancelRecording(): Future[PCMRecorder.CompletionCause]
  def onLengthLimitReached(f: => Unit): Unit // recording is stopped and all resources closed when this is called
  def onError(f: Throwable => Unit): Unit // recording is stopped and all resources closed when this is called
  def maxAmplitudeSinceLastCall: Short
}

object PCMRecorder extends DerivedLogTag {
  import Threading.Implicits.Background

  def startRecording(destination: File, maxDuration: FiniteDuration): PCMRecorder = {
    val limit = (maxDuration.toMillis * PCM.sampleRate) / 1000L
    val writer = new AsyncFileWriter(destination)
    val recorder = new AudioRecord(AudioSource.MIC, PCM.sampleRate, PCM.inputChannelConfig, PCM.sampleFormat, recorderBufferSize)
    val maxAmplitude = new AtomicReference[Short](0)
    @volatile var completionRequest = Option.empty[CompletionCause]

    verbose(l"created new audio recorder (buffer size: $recorderBufferSize)")

    def extremumOf(a: Short, b: Short) = if (abs(a.toInt) > abs(b.toInt)) a else b

    def updateMaxAmplitude(sb: ShortBuffer): Unit = Future {
      var e: Short = 0
      while (sb.hasRemaining) {
        val n = sb.get()
        e = extremumOf(e, n)
      }
      compareAndSet(maxAmplitude)(extremumOf(e, _))
    }

    val recordingCompletion =
      Future {
        val buffer = Array.ofDim[Short](readBufferSize)

        @tailrec def scoop(totalSamples: Long): CompletionCause =
          if (completionRequest.isDefined) completionRequest.get
          else {
            val numberOfShortsReadOrError = recorder.read(buffer, 0, readBufferSize)

            if (numberOfShortsReadOrError < 0) throw new RuntimeException(s"audio recorder indicated error: $numberOfShortsReadOrError")
            else if (totalSamples + numberOfShortsReadOrError > limit) LengthLimitReached
            else {
              val bytes = ByteBuffer.allocateDirect(numberOfShortsReadOrError * SizeOf.SHORT).order(LITTLE_ENDIAN)
              val shorts = bytes.asShortBuffer.put(buffer, 0, numberOfShortsReadOrError)
              shorts.flip()
              updateMaxAmplitude(shorts)
              bytes.rewind()
              writer.enqueue(bytes)
              scoop(totalSamples + numberOfShortsReadOrError)
            }
          }

        recorder.startRecording()
        verbose(l"audio recording started")
        returning(scoop(0L)) { cause =>
          recorder.stop()
          verbose(l"audio recording stopped: $cause")
        }
      }(IO).flatMap { cause =>
        writer.finish()
        writer.completion.map(_ => cause)
      }.andThenFuture { case Failure(cause) =>
        error(l"audio recording (to $destination) failed", cause)
        writer.finish()
        writer.completion
      }.andThen { case _ =>
        verbose(l"releasing audio recorder")
        recorder.release()
      }

    new PCMRecorder {
      override def stopRecording: Future[CompletionCause] = {
        completionRequest = Some(StoppedByUser)
        recordingCompletion
      }

      override def cancelRecording: Future[CompletionCause] = {
        completionRequest = Some(Cancelled)
        recordingCompletion
      }

      override def onLengthLimitReached(f: => Unit): Unit = recordingCompletion.andThen {
        case Success(LengthLimitReached) => f
      }

      override def onError(f: Throwable => Unit): Unit = recordingCompletion.andThen {
        case Failure(cause) => f(cause)
      }

      override def maxAmplitudeSinceLastCall: Short = maxAmplitude.getAndSet(0).toShort
    }
  }

  val recorderMinBufferSize = getMinBufferSize(PCM.sampleRate, PCM.inputChannelConfig, PCM.sampleFormat)
  val recorderBufferSize = max(1 << 16, recorderMinBufferSize)
  val readBufferSize = 1 << 13

  sealed trait CompletionCause extends SafeToLog
  case object StoppedByUser extends CompletionCause
  case object Cancelled extends CompletionCause
  case object LengthLimitReached extends CompletionCause
}
