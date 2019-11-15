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
import java.nio.ByteBuffer

import android.content.Context
import android.media.MediaCodec.CodecException
import android.media._
import android.os.Build
import android.view.Surface
import com.waz.log.LogSE._
import com.waz.api.impl.ProgressIndicator.{ProgressData, ProgressReporter}
import com.waz.bitmap.video.VideoTranscoder.CodecResponse._
import com.waz.bitmap.video.VideoTranscoder.{CodecResponse, MediaCodecIterator}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.model.{AssetMetaData, Dim2}
import com.waz.threading.CancellableFuture
import com.waz.utils.Deprecated.{codecInfoAtIndex, numberOfCodecs}
import com.waz.utils.wrappers.URI
import com.waz.utils.{Cleanup, Managed, returning}

import scala.concurrent.Promise
import scala.util.Try

trait VideoTranscoder {
  def apply(input: URI, out: File, callback: ProgressData => Unit): CancellableFuture[File]
}

object VideoTranscoder {

  val BaseVideoSize = 320
  val MaxFileSizeBytes = 20 * 1024 * 1024 // smaller than backend limit to account for audio and other overhead
  val MaxVideoBitRate = 1024 * 1024

  val OUTPUT_VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
  val OUTPUT_VIDEO_FRAME_RATE = 30
  val OUTPUT_VIDEO_IFRAME_INTERVAL = 10
  val OUTPUT_VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
  val OUTPUT_AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
  val OUTPUT_AUDIO_CHANNEL_COUNT = 1
  val OUTPUT_AUDIO_BIT_RATE = 96 * 1024
  val OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
  val SAMPLE_RATE_48KHZ = 48000
  val SAMPLE_RATE_8KHZ  = 8000
  val SAMPLE_RATE_16KHZ = 16000

  implicit lazy val ExtractorCleanup = new Cleanup[MediaExtractor] {
    override def apply(a: MediaExtractor): Unit = a.release()
  }

  def apply(context: Context): VideoTranscoder =
    new VideoTranscoder18(context)

  trait OutputWriter {
    /**
      * Write available data to output.
      *
      * @return true if there is more data to write
      */
    def advance(): Boolean

    def positionMillis: Long
  }

  type MediaCodecIterator = Iterator[CodecResponse]

  sealed trait CodecResponse extends SafeToLog
  object CodecResponse {
    case object TryAgain extends CodecResponse
    case class FormatChanged(format: MediaFormat) extends CodecResponse
    case class CodecBuffer(codec: MediaCodec, bufferIndex: Int, buffer: ByteBuffer, format: Option[MediaFormat], eof: Boolean, info: MediaCodec.BufferInfo) extends CodecResponse {
      def release() = codec.releaseOutputBuffer(bufferIndex, false)
    }
  }
}

abstract class BaseTranscoder(context: Context) extends VideoTranscoder with DerivedLogTag {
  import VideoTranscoder._
  private implicit val ec = com.waz.threading.Threading.IO

  def apply(input: URI, out: File, callback: ProgressData => Unit): CancellableFuture[File] = CancellableFuture {

    for {
      extractor   <- Managed(returning(new MediaExtractor()) { _.setDataSource(context, URI.unwrap(input), null)})
      videoTrack  = videoTrackIndex(extractor)
      audioTrack  = audioTrackIndex(extractor)
      _           = extractor.selectTrack(videoTrack)
      videoFormat = extractor.getTrackFormat(videoTrack)
      audio       <- if (audioTrack < 1) Managed(Iterator.empty)(Cleanup.empty) else audioStream(input, audioTrack)
      meta        = getVideoMeta(input).fold(m => throw new Exception(s"Couldn't load video metadata: $m"), identity)
      video       <- videoStream(extractor, videoFormat, outputVideoFormat(videoFormat, meta))
      writer      <- createWriter(out, Seq(video, audio).filter(_.nonEmpty): _*)
    } yield (writer, meta)
  } flatMap { writer =>
    val p = Promise[File]()

    p tryComplete Try(writer.acquire { case (w, meta) =>
      val progress = new ProgressReporter(callback, meta.duration.toMillis)
      progress.running(0)
      while (!p.isCompleted && w.advance()) {
        progress.running(w.positionMillis)
      }
      progress.completed()
      out
    })
    new CancellableFuture(p)
  }

  def audioStream(input: URI, audioTrack: Int): Managed[MediaCodecIterator] = {
    def outputAudioFormat(inputFormat: MediaFormat) = {
      val inputChannels = Try(inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) getOrElse 1
      val inputSampleRate = Try(inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)) getOrElse SAMPLE_RATE_48KHZ

      verbose(l"inputChannels: $inputChannels")
      verbose(l"inputSampleRate: $inputSampleRate")

      val (channels, sampleRate, resample) = (inputChannels, inputSampleRate) match {
        case (1, SAMPLE_RATE_8KHZ) =>
          // 8kHz is not supported by default encoder, we should re-sample to 16kHz
          (1, SAMPLE_RATE_16KHZ, true)
        case (2, SAMPLE_RATE_8KHZ) =>
          // will squash two channels into one, this way we don't need to re-sample and encoder will treat it is 16kHz mono stream
          // XXX: this is very ugly, but this is an edge case and we don't want to do proper re-sampling
          (1, SAMPLE_RATE_16KHZ, false)
        case (ch, sr) =>
          (ch, sr, false)
      }

      val format = MediaFormat.createAudioFormat(OUTPUT_AUDIO_MIME_TYPE, sampleRate, channels)
      format.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_AUDIO_BIT_RATE)
      format.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE)
      (format, resample)
    }


    for {
      extractor   <- Managed(returning(new MediaExtractor()) { _.setDataSource(context, URI.unwrap(input), null) })
      _           = extractor.selectTrack(audioTrack)
      inputFormat = extractor.getTrackFormat(audioTrack)
      (outputFormat, dupSamples) = outputAudioFormat(inputFormat)
      decoder <- Managed(new MediaCodecHelper(createAudioDecoder(inputFormat)))
      encoder <- Managed(new MediaCodecHelper(createEncoder(outputFormat)))
    } yield {
      decoder.codec.start()
      encoder.codec.start()

      new TrackEncoder(new TrackDecoder(extractor, decoder), encoder) {
        override def processFrame(frame: CodecBuffer) = processAudioFrame(frame, encoder, dupSamples)
      }
    }
  }

  def videoStream(extractor: MediaExtractor, inputFormat: MediaFormat, outputFormat: MediaFormat): Managed[MediaCodecIterator]

  def createWriter(out: File, tracks: MediaCodecIterator*): Managed[OutputWriter]

  def outputVideoFormat(inputFormat: MediaFormat, meta: AssetMetaData.Video) = {
    def clamp(size: Int) = size / 16 * 16 // limit video size to multiply of 16 to avoid potential problems with codecs

    val dim = meta.dimensions match {
      case Dim2(0, 0) => Dim2(BaseVideoSize, BaseVideoSize * 3 / 2) // FIXME: it's possible that metadata is broken on some devices, we should compute that only when actual frame is decoded
      case Dim2(w, h) if w <= h =>
        val width = clamp(math.min(w, BaseVideoSize))
        Dim2(width, clamp(h * width / w))
      case Dim2(w, h) =>
        val height = clamp(math.min(h, BaseVideoSize))
        Dim2(clamp(w * height / h), clamp(height))
    }

    val videoBitRate =
      if (meta.duration.getSeconds <= 0) MaxVideoBitRate // FIXME: metadata is not always reliable
      else math.min(MaxVideoBitRate, clamp((MaxFileSizeBytes * 8 / meta.duration.getSeconds).toInt))

    verbose(l"input video frame rate: ${Try(inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE))}")

    returning(MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, dim.width, dim.height)) { format =>
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT)
      format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate)
      format.setInteger(MediaFormat.KEY_FRAME_RATE, Try(inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE)) getOrElse OUTPUT_VIDEO_FRAME_RATE)
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL)
      format.setInteger(MediaFormat.KEY_MAX_WIDTH, dim.width)
      format.setInteger(MediaFormat.KEY_MAX_HEIGHT, dim.height)
      format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, dim.width * dim.height)
    }
  }

  def getVideoMeta(uri: URI) = {
    val retriever = new MediaMetadataRetriever
    try {
      retriever.setDataSource(context, URI.unwrap(uri))
      AssetMetaData.Video(retriever)
    } finally
      retriever.release()
  }

  def createVideoDecoder(inputFormat: MediaFormat, surface: Surface): MediaCodec =
    returning(MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat))) { _.configure(inputFormat, surface, null, 0) }

  def createAudioDecoder(inputFormat: MediaFormat): MediaCodec =
    returning(MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat))) { _.configure(inputFormat, null, null, 0) }

  def createEncoder(format: MediaFormat): MediaCodec = {

    val mime = format.getString(MediaFormat.KEY_MIME)

    def supportsFormat(i: MediaCodecInfo) = i.getSupportedTypes.exists(_.equalsIgnoreCase(mime))

    def mediaCodecInfo(): MediaCodecInfo = infos.find(i => i.isEncoder && supportsFormat(i)).get

    returning(MediaCodec.createByCodecName(mediaCodecInfo().getName)) { _.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
  }


  def videoTrackIndex(extractor: MediaExtractor): Int =
    Iterator.tabulate(extractor.getTrackCount)(extractor.getTrackFormat).indexWhere(isVideoFormat)

  def audioTrackIndex(extractor: MediaExtractor): Int =
    Iterator.tabulate(extractor.getTrackCount)(extractor.getTrackFormat).indexWhere(isAudioFormat)


  def processAudioFrame(frame: CodecBuffer, encoder: MediaCodecHelper, duplicate: Boolean) =
    encoder.withInputBuffer {
      case (codec, encIndex, encBuffer) =>
        val info = frame.info
        val size = info.size
        val presentationTime = info.presentationTimeUs
        if (size >= 0) {
          val decBuffer = frame.buffer.duplicate
          decBuffer.position(info.offset)
          decBuffer.limit(info.offset + size)
          encBuffer.position(0)
          if (duplicate && encBuffer.limit() >= size * 2) {
            //XXX: this is an ugly hack for 8kHz mono input, we are simply duplicating samples to receive 16kHz output
            for (_ <- 0 until size / 2) {
              val s1 = decBuffer.get
              val s2 = decBuffer.get
              encBuffer.put(s1)
              encBuffer.put(s2)
              encBuffer.put(s1)
              encBuffer.put(s2)
            }
            codec.queueInputBuffer(encIndex, 0, size * 2, presentationTime, info.flags)
          } else {
            encBuffer.put(decBuffer)
            codec.queueInputBuffer(encIndex, 0, size, presentationTime, info.flags)
          }
        }
        frame.release()
    } .isDefined


  private def isVideoFormat(format: MediaFormat) = getMimeTypeFor(format).startsWith("video/")

  private def isAudioFormat(format: MediaFormat) = getMimeTypeFor(format).startsWith("audio/")

  private def getMimeTypeFor(format: MediaFormat) = format.getString(MediaFormat.KEY_MIME)

  private lazy val infos = Vector.tabulate(numberOfCodecs)(codecInfoAtIndex)
}

class TrackDecoder(extractor: MediaExtractor, decoder: MediaCodecHelper) extends Iterator[Option[CodecBuffer]] with DerivedLogTag {
  override def hasNext: Boolean = decoder.hasNext

  override def next(): Option[CodecBuffer] = {
    extractFrame()
    decoder.next() match {
      case b: CodecBuffer => Some(b)
      case _ => None
    }
  }

  private def extractFrame(): Unit =
    decoder.withInputBuffer { case (codec, index, buffer) =>
      val size = extractor.readSampleData(buffer, 0)
      val presentationTime = extractor.getSampleTime
      if (size >= 0) {
        codec.queueInputBuffer(index, 0, size, presentationTime, extractor.getSampleFlags)
      }
      val done = !extractor.advance
      if (done) try {
        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      } catch {
        case ex: CodecException => error(l"Error while extracting frame", ex)
      }
    }
}

abstract class TrackEncoder(input: Iterator[Option[CodecBuffer]], encoder: MediaCodecHelper) extends MediaCodecIterator {

  private val reader = input.buffered

  override def hasNext: Boolean = encoder.hasNext

  override def next(): CodecResponse = {
    if (reader.hasNext && reader.head.forall(processFrame))
      reader.next()

    encoder.next()
  }

  /**
    * @param frame - decoded input frame
    * @return true if frame was consumed
    */
  def processFrame(frame: CodecBuffer): Boolean
}
