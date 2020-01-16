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

import java.nio.{ByteBuffer, ByteOrder}
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

import android.content.Context
import android.media.{MediaCodec, MediaExtractor, MediaFormat}
import com.waz.bitmap.video.{MediaCodecHelper, TrackDecoder}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.log.LogSE._
import com.waz.model.AssetMetaData
import com.waz.model.Mime
import com.waz.threading.CancellableFuture.{CancelException, DefaultCancelException}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.URI
import com.waz.utils.{Cleanup, ContentURIs, Managed, RichFuture, SizeOf, returning}

import scala.concurrent.duration._
import scala.math._
import scala.util.control.NonFatal

case class AudioLevels(context: Context) extends DerivedLogTag {
  import AudioLevels._
  import Threading.Implicits.Background

  def createAudioOverview(content: URI, mime: Mime, numBars: Int = 100): CancellableFuture[Option[AssetMetaData.Loudness]] =
    if (mime == Mime.Audio.PCM) createPCMAudioOverview(content, numBars)
    else createOtherAudioOverview(content, numBars)

  private def createPCMAudioOverview(content: URI, numBars: Int): CancellableFuture[Option[AssetMetaData.Loudness]] =
    ContentURIs.queryContentUriMetaData(context, content).map(_.size).lift.flatMap {
      case None =>
        warn(l"cannot generate preview: no length available for $content")
        CancellableFuture.successful(None)
      case Some(length) =>
        val samples = length / SizeOf.SHORT
        val cancelRequested = new AtomicBoolean
        returning(CancellableFuture {
          val overview = Managed(context.getContentResolver.openInputStream(URI.unwrap(content))) map { stream =>
            val estimatedBucketSize = round(samples / numBars.toDouble)
            val buffer = ByteBuffer.wrap(Array.ofDim[Byte](8 << 10))

            // will contain at least 1 RMS value per buffer, but more if needed (up to numBars in case there is only 1 buffer)
            val rmsOfBuffers = Iterator.continually(stream.read(buffer.array)).takeWhile(_ >= 0).flatMap { bytesRead =>
              if (cancelRequested.get) throw DefaultCancelException
              buffer.position(0).limit(bytesRead)
              AudioLevels.rms(buffer, estimatedBucketSize, ByteOrder.LITTLE_ENDIAN)
            }.toArray

            loudnessOverview(numBars, rmsOfBuffers) // select RMS peaks and convert to an intuitive scale
          }

          overview.acquire(levels => Some(AssetMetaData.Loudness(levels)))
        }(Threading.IO).recover {
          case c: CancelException => throw c
          case NonFatal(cause) =>
            error(l"PCM overview generation failed", cause)
            None
        })(_.onCancelled(cancelRequested.set(true)))
    }

  private def createOtherAudioOverview(content: URI, numBars: Int): CancellableFuture[Option[AssetMetaData.Loudness]] = {
    val cancelRequested = new AtomicBoolean
    returning(CancellableFuture {
      val overview = for {
        extractor <- Managed(new MediaExtractor)
        trackInfo  = extractAudioTrackInfo(extractor, content)
        helper    <- Managed(new MediaCodecHelper(audioDecoder(trackInfo)))
        _          = helper.codec.start()
        decoder    = new TrackDecoder(extractor, helper)
      } yield {
        val estimatedBucketSize = round((trackInfo.samples / numBars.toDouble) * trackInfo.channels.toDouble)

        // will contain at least 1 RMS value per buffer, but more if needed (up to numBars in case there is only 1 buffer)
        val rmsOfBuffers = decoder.flatten.flatMap { buf =>
          if (cancelRequested.get) throw DefaultCancelException
          returning(AudioLevels.rms(buf.buffer, estimatedBucketSize, ByteOrder.nativeOrder))(_ => buf.release())
        }.toArray

        loudnessOverview(numBars, rmsOfBuffers) // select RMS peaks and convert to an intuitive scale
      }

      overview.acquire(levels => Some(AssetMetaData.Loudness(levels)))
    }(Threading.Background).recover {
      case c: CancelException => throw c
      case NonFatal(cause) =>
        error(l"overview generation failed", cause)
        None
    })(_.onCancelled(cancelRequested.set(true)))
  }

  private def extractAudioTrackInfo(extractor: MediaExtractor, content: URI): TrackInfo = {
    debug(l"data source: $content")
    extractor.setDataSource(context, URI.unwrap(content), null)
    debug(l"track count: ${extractor.getTrackCount}")

    val audioTrack = Iterator.range(0, extractor.getTrackCount).map { n =>
      val fmt = extractor.getTrackFormat(n)
      val m = fmt.getString(MediaFormat.KEY_MIME)
      (n, fmt, m)
    }.find(_._3.toLowerCase(Locale.US).startsWith("audio/"))

    require(audioTrack.isDefined, "media should contain at least one audio track")

    val Some((trackNum, format, mime)) = audioTrack

    extractor.selectTrack(trackNum)

    def get[A](k: String, f: MediaFormat => String => A) = if (format.containsKey(k)) f(format)(k) else throw new NoSuchElementException(s"media format does not contain information about '$k'; mime = '$mime'; URI = $content")

    val samplingRate = get(MediaFormat.KEY_SAMPLE_RATE, _.getInteger)
    val channels = get(MediaFormat.KEY_CHANNEL_COUNT, _.getInteger)
    val duration = get(MediaFormat.KEY_DURATION, _.getLong)
    val samples = duration.toDouble * 1E-6d * samplingRate.toDouble

    returning(TrackInfo(trackNum, format, mime, samplingRate, channels, duration.micros, samples))(ti => debug(l"audio track: $ti"))
  }

  private def audioDecoder(info: TrackInfo): MediaCodec = returning(MediaCodec.createDecoderByType(info.mime)) { mc =>
    mc.configure(info.format, null, null, 0)
  }
}

object AudioLevels {
  case class TrackInfo(index: Int, format: MediaFormat, mime: String, samplingRate: Int, channels: Int, duration: FiniteDuration, samples: Double) extends SafeToLog

  def loudnessOverview(buckets: Int, rmsOfBuffers: Array[Double]): Vector[Float] = {
    val windowLength = max(1, rmsOfBuffers.length / buckets)
    rmsOfBuffers.sliding(windowLength, windowLength).map(peakRmsLoudness).to[Vector].padTo(buckets, 0f)
  }

  def peakRmsLoudness(window: Array[Double]): Float = loudness(dbfsSine(window.max)).toFloat
  def peakLoudness(peak: Int): Float = loudness(dbfsSquare(intAsDouble(peak))).toFloat

  final def rms(bytes: ByteBuffer, estimatedBucketSize: Double, order: ByteOrder = ByteOrder.nativeOrder): Array[Double] =
    if (! bytes.hasRemaining) Array.empty[Double]
    else {
      val ns = bytes.order(order).asShortBuffer
      val len = ns.remaining

      val buckets = ceil(len.toDouble / estimatedBucketSize.toDouble)

      val d = len.toDouble / buckets
      val levels = Array.ofDim[Double](buckets.toInt)

      var bucket = 0
      while (bucket < levels.length) {
        val end = ((bucket + 1) * d).toInt
        val start = (bucket * d).toInt
        var i = start
        var sum = 0d
        do {
          val n = ns.get(i)
          val f = if (n < 0) n / 32768d else n / 32767d
          sum += f * f
          i += 1
        } while (i < end)
        levels(bucket) = sqrt(sum / max(1d, end - start).toDouble)
        bucket += 1
      }

      levels
    }

  def intAsDouble(sample: Int): Double = {
    val n = min(max(minInt, sample), maxInt)
    if (n < 0) n / -32768d else n / 32767d
  }

  /* This is not actually loudness as defined by the standards behind lufs/lkfs, instead, it follows the non-exact
   * rule-of-thumb that every 10 dB increase is a doubling of perceived loudness. */
  def loudness(dbfs: Double): Double = pow(2d, min(dbfs, 0d) / 10d)

  def dbfsSquare(n: Double): Double = 20d * log10(abs(n))
  def dbfsSine(n: Double): Double = dbfsSquare(n) + 3d

  private val minInt = Short.MinValue.toInt
  private val maxInt = Short.MaxValue.toInt

  implicit lazy val MediaExtractorCleanedUp: Cleanup[MediaExtractor] = new Cleanup[MediaExtractor] {
    override def apply(a: MediaExtractor): Unit = a.release()
  }

  implicit lazy val MediaCodecCleanedUp: Cleanup[MediaCodec] = new Cleanup[MediaCodec] {
    override def apply(a: MediaCodec): Unit = a.release()
  }
}
