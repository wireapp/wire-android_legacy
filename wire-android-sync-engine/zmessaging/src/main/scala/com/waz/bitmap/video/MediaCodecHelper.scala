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

import java.nio.ByteBuffer

import android.media.{MediaCodec, MediaFormat}
import com.waz.bitmap.video.VideoTranscoder.CodecResponse.{CodecBuffer, FormatChanged, TryAgain}
import com.waz.bitmap.video.VideoTranscoder.{CodecResponse, MediaCodecIterator}
import com.waz.utils.Cleanup
import com.waz.utils.Deprecated.{INFO_OUTPUT_BUFFERS_CHANGED, inputBuffersOf, outputBuffersOf}

import scala.util.Try

class MediaCodecHelper(val codec: MediaCodec) extends MediaCodecIterator {
  import MediaCodecHelper._

  private val outputBufferInfo = new MediaCodec.BufferInfo

  private lazy val inputBuffers = inputBuffersOf(codec)
  private var outputBuffers = Array.empty[ByteBuffer]

  private var outputFormat = Option.empty[MediaFormat]
  private var eof = false

  def withInputBuffer[A](body: (MediaCodec, Int, ByteBuffer) => A): Option[A] =
    codec.dequeueInputBuffer(inputDequeueTimeoutMicros) match {
      case MediaCodec.INFO_TRY_AGAIN_LATER => None
      case index => Some(body(codec, index, inputBuffers(index)))
    }

  override def hasNext: Boolean = !eof

  override def next(): CodecResponse =
    codec.dequeueOutputBuffer(outputBufferInfo, outputDequeueTimeoutMicros) match {
      case MediaCodec.INFO_TRY_AGAIN_LATER => TryAgain
      case INFO_OUTPUT_BUFFERS_CHANGED =>
        outputBuffers = outputBuffersOf(codec)
        TryAgain
      case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED =>
        outputFormat = Some(codec.getOutputFormat)
        FormatChanged(outputFormat.get)
      case index =>
        if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          codec.releaseOutputBuffer(index, false)
          TryAgain
        } else {
          eof = (outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          if (outputBuffers.isEmpty) outputBuffers = outputBuffersOf(codec)
          CodecBuffer(codec, index, outputBuffers(index), outputFormat, eof, outputBufferInfo)
        }
    }
}

object MediaCodecHelper {
  val inputDequeueTimeoutMicros: Int = 15000
  val outputDequeueTimeoutMicros = 1500


  implicit object Cleanup extends Cleanup[MediaCodecHelper] {
    override def apply(a: MediaCodecHelper): Unit = {
      Try(a.codec.stop())
      a.codec.release()
    }
  }
}
