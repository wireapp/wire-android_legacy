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

import java.io.FileInputStream
import java.lang.Thread.UncaughtExceptionHandler
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN

import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioTrack
import android.media.AudioTrack.{MODE_STREAM, OnPlaybackPositionUpdateListener, getMinBufferSize}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.service.assets.GlobalRecordAndPlayService.{MediaPointer, PCMContent}
import com.wire.signals.DispatchQueue
import com.waz.threading.Threading
import PCM.SizeOfShort
import org.threeten.bp

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.math.{max, min}
import scala.util.Try

class PCMPlayer private (content: PCMContent, track: AudioTrack, totalSamples: Long, stream: FileInputStream, observer: Player.Observer) extends Player {
  import PCMPlayer._

  private implicit val dispatcher = DispatchQueue(DispatchQueue.SERIAL, Threading.IO, None)
  private val buffer = ByteBuffer.allocateDirect(bufferSizeInShorts * SizeOfShort).order(LITTLE_ENDIAN)
  private def channel = stream.getChannel

  @volatile private var scooping = Option.empty[ScoopThread]
  @volatile private var playheadOffset = 0L
  @volatile private var currentBuffer = Array.empty[Short]
  @volatile private var currentOffsetInBuffer = 0

  override def start(): Future[Unit] = Future {
    track.play()
    startScooping()
  }

  override def resume(): Future[Unit] = start()

  override def pause(): Future[MediaPointer] = Future {
    track.pause()
    stopScooping()
    MediaPointer(content, playheadUnsafe)
  }

  override def repositionPlayhead(pos: bp.Duration): Future[Unit] = Future {
    val pauseAndResume = track.getState == AudioTrack.PLAYSTATE_PLAYING

    if (pauseAndResume) {
      stopScooping()
      track.pause()
    }

    val newPlayhead = max(0, min(totalSamples - (playerBufferSize / SizeOfShort), (pos.toMillis * PCM.sampleRate) / 1000))
    track.flush()
    playheadOffset = playheadInSamples - newPlayhead
    channel.position(newPlayhead * SizeOfShort)
    track.setNotificationMarkerPosition((playheadInSamples + totalSamples - newPlayhead).toInt)

    if (pauseAndResume) {
      track.play()
      startScooping()
    }
  }

  override def playhead: Future[bp.Duration] = Future(playheadUnsafe)

  private def playheadUnsafe = PCM.durationFromSampleCount(playheadInSamples - playheadOffset)
  private def playheadInSamples = track.getPlaybackHeadPosition.toLong & 0xFFFFFFFFL

  override def release(): Future[Unit] = Future(releaseUnsafe)

  override protected def finalize: Unit = releaseUnsafe

  private def releaseUnsafe: Unit = {
    scooping.foreach(_.stopPlaying())
    scooping = None
    Try(track.release())
    Try(stream.close())
    currentBuffer = Array.empty
    currentOffsetInBuffer = 0
  }

  private def startScooping(): Unit = {
    stopScooping()
    val t = new ScoopThread
    scooping = Some(t)
    t.start()
  }

  private def stopScooping(): Unit = {
    scooping.foreach { t =>
      t.stopPlaying
      t.join()
    }
    scooping = None
  }

  class ScoopThread extends Thread with DerivedLogTag {
    @volatile private var playing = false

    setUncaughtExceptionHandler(new UncaughtExceptionHandler {
      override def uncaughtException(thread: Thread, cause: Throwable): Unit = error(l"scooping [$thread] stopped due to exception", cause)
    })

    override def run: Unit = {
      verbose(l"scooping started")
      playing = true
      scoop()
    }

    def stopPlaying(): Unit = playing = false

    @tailrec private def scoop(): Unit = if (playing) {
      if (currentBuffer.isEmpty) {
        buffer.rewind()
        val bytesRead = channel.read(buffer)
        buffer.flip()
        currentBuffer = Array.ofDim(bytesRead / SizeOfShort)
        currentOffsetInBuffer = 0
        buffer.asShortBuffer.get(currentBuffer)
      }

      val written = track.write(currentBuffer, currentOffsetInBuffer, currentBuffer.size - currentOffsetInBuffer)
      if (written < 0) {
        observer.onError(s"AudioTrack write error: $written")
      } else {
        currentOffsetInBuffer += written
        if (currentOffsetInBuffer >= currentBuffer.size) {
          currentBuffer = Array.empty
          currentOffsetInBuffer = 0
        }
        scoop()
      }
    } else verbose(l"scooping stopped")
  }
}

object PCMPlayer extends DerivedLogTag {
  def apply(content: PCMContent, observer: Player.Observer): Future[PCMPlayer] = Threading.BackgroundHandler.map { handler =>
    val track = new AudioTrack(STREAM_MUSIC, PCM.sampleRate, PCM.outputChannelConfig, PCM.sampleFormat, playerBufferSize, MODE_STREAM)
    verbose(l"created audio track; buffer size: $playerBufferSize")
    val totalSamples = content.file.length / SizeOfShort

    track.setNotificationMarkerPosition(totalSamples.toInt)

    track.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener {
      override def onPeriodicNotification(t: AudioTrack): Unit = ()
      override def onMarkerReached(t: AudioTrack): Unit = {
        verbose(l"EOF reached: $content")
        observer.onCompletion()
      }
    }, handler)

    val stream = try new FileInputStream(content.file) catch {
      case t: Throwable =>
        track.release()
        throw t
    }

    new PCMPlayer(content, track, totalSamples, stream, observer)
  }(Threading.Background)

  val playerMinBufferSize = getMinBufferSize(PCM.sampleRate, PCM.outputChannelConfig, PCM.sampleFormat)
  val playerBufferSize = max(1 << 13, playerMinBufferSize)
  val bufferSizeInShorts = 1 << 10
}
