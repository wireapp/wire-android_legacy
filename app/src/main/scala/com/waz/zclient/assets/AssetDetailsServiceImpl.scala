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
package com.waz.zclient.assets

import java.nio.ByteOrder
import java.util.Locale

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever.{METADATA_KEY_DURATION, METADATA_KEY_VIDEO_HEIGHT, METADATA_KEY_VIDEO_ROTATION, METADATA_KEY_VIDEO_WIDTH}
import android.media._
import com.waz.bitmap.video.{MediaCodecHelper, TrackDecoder}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Dim2, Mime}
import com.waz.service.assets
import com.waz.service.assets.{AssetDetails, AssetDetailsService, AudioDetails, AudioLevels, BlobDetails, ImageDetails, Loudness, PreparedContent, UriHelper, VideoDetails}
import com.waz.service.assets.AudioLevels.{TrackInfo, loudnessOverview}
import com.waz.utils.{IoUtils, _}
import com.waz.zclient.assets.MetadataExtractionUtils._
import com.waz.zclient.log.LogUI._
import org.threeten.bp

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.math.round
import scala.util.{Failure, Success, Try}

class AssetDetailsServiceImpl(uriHelper: UriHelper)
                             (implicit context: Context, ec: ExecutionContext)
  extends AssetDetailsService with DerivedLogTag {

  import AssetDetailsServiceImpl._

  override def extract(content: PreparedContent): (AssetDetails, Mime) = {
    // this is a bit hacky - we know MP4 might mean audio-only,
    // but in case of other audio files we don't know their real mime type,
    // so we use the original one, even though we recognize it as video
    def checkAudio(source: Source, mime: Mime) = extractForAudio(source).map {
      case details if mime == Mime.Video.MP4 => (details, Mime.Audio.MP4)
      case details                           => (details, mime)
    }

    lazy val source = asSource(content)

    content.getMime(uriHelper) match {
      case Success(mime) if Mime.Video.supported.contains(mime) =>
        extractForVideo(source).map { (_, mime) }.orElse(checkAudio(source, mime)).getOrElse(DefaultDetails)
      case Success(mime) if Mime.Audio.supported.contains(mime) =>
        checkAudio(source, mime).getOrElse(DefaultDetails)
      case Success(mime) if Mime.Image.supported.contains(mime) =>
        extractForImage(content).map { (_, mime) }.getOrElse(DefaultDetails)
      case Success(mime) =>
        (BlobDetails, mime)
      case _ =>
        DefaultDetails
    }
  }

  private def extractForImage(content: PreparedContent): Option[ImageDetails] =
    content.openInputStream(uriHelper).map { is =>
      IoUtils.withResource(is) { _ =>
        val opts = new BitmapFactory.Options
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeStream(is, null, opts)

        if (opts.outWidth == -1 || opts.outHeight == -1) None
        else Option(ImageDetails(Dim2(opts.outWidth, opts.outHeight)))
      }
    }.recover { case ex =>
    error(l"Error while extracting image details: $content", ex)
    None
  }.toOption.flatten

  private def extractForVideo(source: Source): Option[VideoDetails] = Try {
    createMetadataRetriever(source).acquire { implicit retriever =>
      val width    = retrieve(METADATA_KEY_VIDEO_WIDTH, "video width", _.toInt)
      val height   = retrieve(METADATA_KEY_VIDEO_HEIGHT, "video height", _.toInt)
      val rotation = retrieve(METADATA_KEY_VIDEO_ROTATION, "video rotation", _.toInt)
      val duration = retrieve(METADATA_KEY_DURATION, "video duration", _.toLong)
      (width, height, rotation, duration) match {
        case (Some(w), Some(h), Some(r), Some(d)) =>
          val dimensions = Dim2(w, h)
          Option(VideoDetails(
            if (shouldSwapDimensions(r)) dimensions.swap else dimensions,
            bp.Duration.ofMillis(d)
          ))
        case _ =>
          error(l"Error while extracting video details: $source")
          None
      }
    }
  }.recover { case ex =>
    error(l"Error while extracting video details: $source", ex)
    None
  }.toOption.flatten

  private def extractForAudio(source: Source, bars: Int = 100): Option[AudioDetails] =
    Try {
      createMetadataRetriever(source).acquire { implicit retriever =>
        val duration = retrieve(METADATA_KEY_DURATION, "audio duration", _.toLong)
        val loudness = extractAudioLoudness(source, bars)
        (duration, loudness) match {
          case (Some(d), Some(l)) => Option(assets.AudioDetails(bp.Duration.ofMillis(d), l))
          case _ =>
            error(l"Error while extracting audio details: $source")
            None
        }
      }
    }.recover { case ex =>
      error(l"Error while extracting audio details: $source", ex)
      None
    }.toOption.flatten

  private def extractAudioLoudness(source: Source, numBars: Int): Option[Loudness] =
    Try {
      val overview = for {
        extractor <- createMediaExtractor(source)
        trackInfo  = extractAudioTrackInfo(extractor, source)
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

      overview.acquire(levels => Loudness(levels.map(_.toByte)))
    } match {
      case Success(res) => Some(res)
      case Failure(err) =>
        error(l"Error while audio levels extraction", err)
        None
    }

  private def extractAudioTrackInfo(extractor: MediaExtractor, source: Source): TrackInfo = {
    debug(l"data source: $source")
    debug(l"track count: ${extractor.getTrackCount}")

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
      else throw new NoSuchElementException(s"media format does not contain information about '$k'; mime = '$mime'; source = $source")

    val samplingRate = get(MediaFormat.KEY_SAMPLE_RATE, _.getInteger)
    val channels = get(MediaFormat.KEY_CHANNEL_COUNT, _.getInteger)
    val duration = get(MediaFormat.KEY_DURATION, _.getLong)
    val samples = duration.toDouble * 1E-6d * samplingRate.toDouble

    returning(TrackInfo(trackNum, format, mime, samplingRate, channels, duration.micros, samples))(ti => debug(l"audio track: $ti"))
  }

}

object AssetDetailsServiceImpl {

  private[assets] val DefaultDetails = (BlobDetails, Mime.Default)

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
