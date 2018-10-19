/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.assets2

import java.net.URI
import java.nio.ByteOrder
import java.util.Locale

import android.content.Context
import android.graphics.{Bitmap, BitmapFactory}
import android.media.MediaMetadataRetriever.{METADATA_KEY_DURATION, METADATA_KEY_VIDEO_HEIGHT, METADATA_KEY_VIDEO_ROTATION, METADATA_KEY_VIDEO_WIDTH}
import android.media._
import android.net.Uri
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.bitmap.video.{MediaCodecHelper, TrackDecoder}
import com.waz.model.Dim2
import com.waz.service.assets.AudioLevels
import com.waz.service.assets.AudioLevels.{TrackInfo, loudnessOverview, _}
import com.waz.service.assets2._
import com.waz.utils.{IoUtils, _}
import org.threeten.bp

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.round
import scala.util.{Failure, Success, Try}


class MetadataServiceImpl(context: Context, uriHelper: UriHelper)
                         (implicit ec: ExecutionContext) extends MetadataService {
  import MetadataServiceImpl._

  private def withMetadataRetriever[A](uri: URI)(f: MediaMetadataRetriever => A): Future[A] =
    Future {
      Managed(new MediaMetadataRetriever).acquire { retriever =>
        val androidUri = Uri.parse(uri.toString)
        retriever.setDataSource(context, androidUri)
        f(retriever)
      }
    }

  //TODO We should try to remove this method
  def extractForImage(bm: Bitmap, orientation: Int, tag: ImageTag): Future[ImageDetails] =
    Future {
      val dimensions = Dim2(bm.getWidth, bm.getHeight)
      ImageDetails(
        if (shouldSwapDimensionsExif(orientation)) dimensions.swap else dimensions,
        tag
      )
    }

  override def extractForImage(uri: URI, tag: ImageTag): Future[ImageDetails] =
    Future.fromTry(uriHelper.openInputStream(uri)).flatMap { is =>
      IoUtils.withResource(is) { _ =>
        val opts = new BitmapFactory.Options
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeStream(is, null, opts)

        if (opts.outWidth == -1)
          Future.failed(new IllegalArgumentException("can not extract image width"))
        else if (opts.outHeight == -1)
          Future.failed(new IllegalArgumentException("can not extract image height"))
        else
          Future.successful(ImageDetails(Dim2(opts.outWidth, opts.outHeight), tag))
      }
    }

  override def extractForVideo(uri: URI): Future[VideoDetails] =
    withMetadataRetriever(uri) { implicit retriever =>
      for {
        width <- retrieve(METADATA_KEY_VIDEO_WIDTH, "video width", _.toInt)
        height <- retrieve(METADATA_KEY_VIDEO_HEIGHT, "video height", _.toInt)
        rotation <- retrieve(METADATA_KEY_VIDEO_ROTATION, "video rotation", _.toInt)
        duration <- retrieve(METADATA_KEY_DURATION, "video duration", s => bp.Duration.ofMillis(s.toLong))
      } yield {
        val dimensions = Dim2(width, height)
        VideoDetails(
          dimensions = if (shouldSwapDimensions(rotation)) dimensions.swap else dimensions,
          duration
        )
      }
    } flatMap {
      case Right(details) => Future.successful(details)
      case Left(msg) => Future.failed(new IllegalArgumentException(msg))
    }

  override def extractForAudio(uri: URI, bars: Int = 100): Future[AudioDetails] =
    withMetadataRetriever(uri) { implicit retriever =>
      for {
        duration <- retrieve(METADATA_KEY_DURATION, "video duration", s => bp.Duration.ofMillis(s.toLong))
        loudness <- extractAudioLoudness(uri, bars)
      } yield {
        AudioDetails(duration, loudness)
      }
    } flatMap {
      case Right(details) => Future.successful(details)
      case Left(msg) => Future.failed(new IllegalArgumentException(msg))
    }

  private def extractAudioLoudness(content: URI, numBars: Int): Either[String, Loudness] =
    Try {
      val overview = for {
        extractor <- Managed(new MediaExtractor)
        trackInfo  = extractAudioTrackInfo(extractor, content)
        helper    <- Managed(new MediaCodecHelper(createAudioDecoder(trackInfo)))
        _          = helper.codec.start()
        decoder    = new TrackDecoder(extractor, helper)
      } yield {
        val estimatedBucketSize = round((trackInfo.samples / numBars.toDouble) * trackInfo.channels.toDouble)

        // will contain at least 1 RMS value per buffer, but more if needed (up to numBars in case there is only 1 buffer)
        val rmsOfBuffers = decoder.flatten.flatMap { buf =>
          returning(AudioLevels.rms(buf.buffer, estimatedBucketSize, ByteOrder.nativeOrder))(_ => buf.release())
        }.toArray

        loudnessOverview(numBars, rmsOfBuffers) // select RMS peaks and convert to an intuitive scale
      }

      overview.acquire(levels => Loudness(levels))
    } match {
      case Success(res) => Right(res)
      case Failure(err) =>
        verbose(s"Error while audio levels extraction: $err")
        Left("can not extract audio levels")
    }

  private def extractAudioTrackInfo(extractor: MediaExtractor, content: URI): TrackInfo = {
    debug(s"data source: $content")

    extractor.setDataSource(context, Uri.parse(content.toString), null)
    debug(s"track count: ${extractor.getTrackCount}")

    val audioTrack = Iterator.range(0, extractor.getTrackCount).map { n =>
      val fmt = extractor.getTrackFormat(n)
      val m = fmt.getString(MediaFormat.KEY_MIME)
      (n, fmt, m)
    }.find(_._3.toLowerCase(Locale.US).startsWith("audio/"))

    require(audioTrack.isDefined, "media should contain at least one audio track")

    val Some((trackNum, format, mime)) = audioTrack

    extractor.selectTrack(trackNum)

    def get[A](k: String, f: MediaFormat => String => A): A =
      if (format.containsKey(k)) f(format)(k)
      else throw new NoSuchElementException(s"media format does not contain information about '$k'; mime = '$mime'; URI = $content")

    val samplingRate = get(MediaFormat.KEY_SAMPLE_RATE, _.getInteger)
    val channels = get(MediaFormat.KEY_CHANNEL_COUNT, _.getInteger)
    val duration = get(MediaFormat.KEY_DURATION, _.getLong)
    val samples = duration.toDouble * 1E-6d * samplingRate.toDouble

    returning(TrackInfo(trackNum, format, mime, samplingRate, channels, duration.micros, samples))(ti => debug(s"audio track: $ti"))
  }

}

object MetadataServiceImpl {

  implicit val RetrieverCleanup: Cleanup[MediaMetadataRetriever] =
    new Cleanup[MediaMetadataRetriever] {
      override def apply(a: MediaMetadataRetriever): Unit = a.release()
    }

  def retrieve[A](key: Int, tag: String, convert: String => A)
                 (implicit retriever: MediaMetadataRetriever): Either[String, A] =
    for {
      s <- Option(retriever.extractMetadata(key)).toRight(s"$tag ($key) is null")
      result <- Try(convert(s)).toRight(t => s"unable to convert $tag ($key) of value '$s': ${t.getMessage}")
    } yield result

  def createAudioDecoder(info: TrackInfo): MediaCodec =
    returning(MediaCodec.createDecoderByType(info.mime)) { mc =>
      mc.configure(info.format, null, null, 0)
    }

  def shouldSwapDimensions(rotation: Int): Boolean = {
    val orientation = rotation match {
      case 90  => ExifInterface.ORIENTATION_ROTATE_90
      case 180 => ExifInterface.ORIENTATION_ROTATE_180
      case 270 => ExifInterface.ORIENTATION_ROTATE_270
      case _   => ExifInterface.ORIENTATION_NORMAL

    }
    shouldSwapDimensionsExif(orientation)
  }

  def shouldSwapDimensionsExif(orientation: Int): Boolean = orientation match {
    case ExifInterface.ORIENTATION_ROTATE_90 |
         ExifInterface.ORIENTATION_ROTATE_270 |
         ExifInterface.ORIENTATION_TRANSPOSE |
         ExifInterface.ORIENTATION_TRANSVERSE => true
    case _ => false
  }

}
