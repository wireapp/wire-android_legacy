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

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.ByteOrder.{BIG_ENDIAN, LITTLE_ENDIAN, nativeOrder}

import android.content.Context
import android.media.MediaCodec.{BUFFER_FLAG_CODEC_CONFIG, BUFFER_FLAG_END_OF_STREAM, CONFIGURE_FLAG_ENCODE}
import android.media.MediaFormat._
import android.media.{MediaCodec, MediaCodecInfo, MediaFormat}
import com.googlecode.mp4parser.FileDataSourceImpl
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl
import com.waz.api.ProgressIndicator.State
import com.waz.api.impl.ProgressIndicator
import com.waz.api.impl.ProgressIndicator.{ProgressData, ProgressReporter}
import com.waz.bitmap.video.MediaCodecHelper.{inputDequeueTimeoutMicros, outputDequeueTimeoutMicros}
import com.waz.service.TempFileService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.Deprecated.{INFO_OUTPUT_BUFFERS_CHANGED, inputBuffersOf, outputBuffersOf}
import com.waz.utils.wrappers.URI
import libcore.io.SizeOf

import scala.concurrent.Promise
import scala.util.Try

class AudioTranscoder(tempFiles: TempFileService, context: Context) {
  import AudioTranscoder._
  import Threading.Implicits.Background

  def apply(uri: URI, mp4File: File, callback: ProgressIndicator.Callback): CancellableFuture[File] =
    ContentURIs.queryContentUriMetaData(context, uri).map(_.size.getOrElse(0L)).lift.flatMap { size =>
      val promisedFile = Promise[File]

      promisedFile.tryComplete(Try {
        Managed(tempFiles.newTempFile).foreach { aac =>
          val progress = if (size <= 0L) {
            callback(ProgressData.Indefinite)
            Option.empty[ProgressReporter]
          } else {
            Some(new ProgressReporter(callback, size))
          }

          transcodeToAAC(uri, aac.file, progress, promisedFile.isCompleted)
          val aacTrack = new AACTrackImpl(new FileDataSourceImpl(aac.file))
          val movie = returning(new Movie)(_.addTrack(aacTrack))
          val container = new DefaultMp4Builder().build(movie)

          if (! promisedFile.isCompleted) Managed(new FileOutputStream(mp4File)).foreach(stream => container.writeContainer(stream.getChannel))
          progress.fold2(callback.apply(ProgressData(0, -1, State.COMPLETED)), _.completed)
        }

        mp4File
      })

      new CancellableFuture(promisedFile)
    } (Threading.BlockingIO)

  import AudioLevels.MediaCodecCleanedUp

  private def transcodeToAAC(uri: URI, aacFile: File, reporter: Option[ProgressReporter], hasBeenCancelled: => Boolean): Unit =
    for {
      in <- Managed(context.getContentResolver.openInputStream(URI.unwrap(uri)))
      outStream <- Managed(new FileOutputStream(aacFile))
      out = outStream.getChannel
      encoder <- Managed(audioEncoder)
    } {
      encoder.start()

      val inputBuffers = inputBuffersOf(encoder)
      val outputBufferInfo = new MediaCodec.BufferInfo

      var outputBuffers = outputBuffersOf(encoder)
      var samplesSoFar = 0L
      var endOfInput = false
      var endOfOutput = false

      while (! endOfOutput && ! hasBeenCancelled) {
        if (! endOfInput) {
          val inputBufferIndex = encoder.dequeueInputBuffer(inputDequeueTimeoutMicros)

          if (inputBufferIndex >= 0) {
            val inputBuffer = inputBuffers(inputBufferIndex).order(nativeOrder)
            val readBuffer = Array.ofDim[Byte](inputBuffer.remaining())
            val bytesRead = in.read(readBuffer)

            if (bytesRead < 0) {
              endOfInput = true
              encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM)
            } else {
              val shorts = ByteBuffer.wrap(readBuffer, 0, bytesRead).order(LITTLE_ENDIAN).asShortBuffer
              val presentationTimeUs = (samplesSoFar / SizeOf.SHORT) * 1000000L / PCM.sampleRate
              samplesSoFar += shorts.remaining()
              inputBuffer.position(0)
              inputBuffer.asShortBuffer.put(shorts)
              encoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0)
            }
          }
        }

        val outputBufferIndex = encoder.dequeueOutputBuffer(outputBufferInfo, outputDequeueTimeoutMicros)

        if (outputBufferIndex >= 0 && (outputBufferInfo.flags & BUFFER_FLAG_CODEC_CONFIG) == 0) {
          val outputBuffer = outputBuffers(outputBufferIndex)
          if (outputBufferInfo.size > 0) {
            outputBuffer.position(outputBufferInfo.offset)
            outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size)

            out.write(adtsHeader(outputBufferInfo.size))
            out.write(outputBuffer)
          }
          encoder.releaseOutputBuffer(outputBufferIndex, false)
          if (outputBufferInfo.presentationTimeUs > 0L) reporter.foreach(_.running(samplesSoFar * SizeOf.SHORT))
          endOfOutput = (outputBufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0
        } else if (outputBufferIndex == INFO_OUTPUT_BUFFERS_CHANGED) {
          outputBuffers = outputBuffersOf(encoder)
        }
      }

      encoder.stop()
    }

  private def audioEncoder: MediaCodec = returning(MediaCodec.createEncoderByType("audio/mp4a-latm")) { mc =>
    mc.configure(aacFormat, null, null, CONFIGURE_FLAG_ENCODE)
  }

  private def aacFormat = returning(new MediaFormat) { format =>
    format.setString(KEY_MIME, "audio/mp4a-latm")
    format.setInteger(KEY_BIT_RATE, bitRate)
    format.setInteger(KEY_CHANNEL_COUNT, 1)
    format.setInteger(KEY_SAMPLE_RATE, sampleRate)
    format.setInteger(KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
  }
}

object AudioTranscoder {
  val bitRate = 1 << 16
  val sampleRate = PCM.sampleRate

  def estimatedSizeBasedOnBitrate(byteCount: Long): Long =
    math.round(((byteCount / SizeOf.SHORT).toDouble / sampleRate.toDouble) * (bitRate.toDouble / 8d)).toLong

  def adtsHeader(aacFrameLength: Int): ByteBuffer = { // see https://wiki.multimedia.cx/index.php?title=ADTS
    val profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    val samplingFrequencyIndex = 4 // 44.1kHz
    val channelConfiguration = 1

    returning(ByteBuffer.allocate(8).order(BIG_ENDIAN).putLong(
      0L ++ (12, 0xFFF) ++ (1, 1) ++ (2, 0) ++ (1, 1)
         ++ (2, profile - 1) ++ (4, samplingFrequencyIndex) ++ (1, 0) ++ (3, channelConfiguration)
         ++ (4, 0) ++ (13, aacFrameLength + 7) ++ (11, 0xFFF) ++ (2, 0)
    ))(_.position(1))
  }

  private implicit class BitAppender(val l: Long) extends AnyVal {
    def ++ (bits: Int, value: Int) = (l << bits) + (((1 << bits) - 1) & value)
  }
}
