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
package com.waz.bitmap.video

import java.io.File

import android.annotation.TargetApi
import android.content.Context
import android.media.{MediaCodec, MediaExtractor, MediaFormat, MediaMuxer}
import android.os.Build
import com.waz.bitmap.video.VideoTranscoder.CodecResponse.{CodecBuffer, FormatChanged, TryAgain}
import com.waz.bitmap.video.VideoTranscoder.{MediaCodecIterator, OutputWriter}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.utils.{Cleanup, Managed, returning}


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class VideoTranscoder18(context: Context) extends BaseTranscoder(context) {

  implicit lazy val MuxerCleanup = new Cleanup[MediaMuxer] {
    override def apply(a: MediaMuxer): Unit = a.release()
  }

  implicit lazy val InputSurfaceCleanup = new Cleanup[InputSurface] {
    override def apply(a: InputSurface): Unit = a.release()
  }

  implicit lazy val OutputSurfaceCleanup = new Cleanup[OutputSurface] {
    override def apply(a: OutputSurface): Unit = a.release()
  }

  override def videoStream(extractor: MediaExtractor, inputFormat: MediaFormat, outputFormat: MediaFormat): Managed[MediaCodecIterator] = {
    val enc = createEncoder(outputFormat)
    for {
      inputSurface <- Managed(returning(new InputSurface(enc.createInputSurface())) { _.makeCurrent()})
      outputSurface <- Managed(new OutputSurface())
      encoder <- Managed(new MediaCodecHelper(enc))
      decoder <- Managed(new MediaCodecHelper(createVideoDecoder(inputFormat, outputSurface.getSurface)))
    } yield {
      decoder.codec.start()
      enc.start()

      new TrackEncoder(new TrackDecoder(extractor, decoder), encoder) {
        override def processFrame(frame: CodecBuffer) = processVideoFrame(frame, enc, inputSurface, outputSurface)
      }
    }
  }

  override def createWriter(out: File, tracks: MediaCodecIterator*) =
    Managed(new MediaMuxer(out.getAbsolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)) map { new MuxerWriter(_, tracks: _*) }


  def processVideoFrame(frame: CodecBuffer, encoder: MediaCodec, inputSurface: InputSurface, outputSurface: OutputSurface) = {
    val CodecBuffer(codec, index, _, _, eof, info) = frame
    if (info.size > 0) {
      codec.releaseOutputBuffer(index, true)
      outputSurface.awaitNewImage()
      outputSurface.drawImage()
      inputSurface.setPresentationTime(info.presentationTimeUs * 1000)
      inputSurface.swapBuffers
    } else
      frame.release()
    if (eof) encoder.signalEndOfInputStream()
    true
  }
}

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class MuxerWriter(muxer: MediaMuxer, sources: MediaCodecIterator*) extends OutputWriter with DerivedLogTag {
  case class SourceWithTrack(source: MediaCodecIterator, var track: Option[Int] = None, var positionMs: Long = 0)

  private val withTracks = sources.map { SourceWithTrack(_) }
  private var started = false

  def positionMillis = withTracks.minBy(_.positionMs).positionMs

  override def advance() =
    if (withTracks.forall(!_.source.hasNext)) false
    else {
      withTracks foreach {
        case s @ SourceWithTrack(source, Some(track), _) if source.hasNext && started =>
          source.next() match {
            case TryAgain =>
            case frame @ CodecBuffer(codec, index, buffer, _, eof, info) =>
              if (info.size > 0) {
                muxer.writeSampleData(track, buffer, info)
                s.positionMs = info.presentationTimeUs / 1000
              }
              frame.release()
            case st => error(l"unexpected state: $st")
          }
        case s @ SourceWithTrack(source, None, _) if source.hasNext && !started =>
          source.next() match {
            case TryAgain =>
            case FormatChanged(format) =>
              s.track = Some(muxer.addTrack(format))
            case st => error(l"unexpected state: $st")
          }
        case _ => // ignore
      }

      if (!started && withTracks.forall(_.track.isDefined)) {
        muxer.start()
        started = true
      }
      true
    }
}
