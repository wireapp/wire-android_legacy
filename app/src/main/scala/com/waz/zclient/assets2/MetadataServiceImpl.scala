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

import android.graphics.{Bitmap, BitmapFactory}
import android.media.MediaMetadataRetriever.{METADATA_KEY_DURATION, METADATA_KEY_VIDEO_HEIGHT, METADATA_KEY_VIDEO_ROTATION, METADATA_KEY_VIDEO_WIDTH}
import android.media.{ExifInterface, MediaMetadataRetriever}
import com.waz.model.Dim2
import com.waz.service.assets2.{ImageDetails, Medium, UriHelper, VideoDetails}
import com.waz.utils.{IoUtils, _}
import org.threeten.bp

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MetadataServiceImpl(uriHelper: UriHelper, metadataRetriever: AndroidMetadataRetriever)
                         (implicit ec: ExecutionContext) {
  import MetadataServiceImpl._

  def extractForImage(bm: Bitmap, orientation: Int): Future[ImageDetails] =
    Future {
      val dimensions = Dim2(bm.getWidth, bm.getHeight)
      ImageDetails(
        dimensions = if (shouldSwapDimensionsExif(orientation)) dimensions.swap else dimensions,
        tag = Medium
      )
    }

  def extractForImage(uri: URI): Future[ImageDetails] =
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
          Future.successful(ImageDetails(Dim2(opts.outWidth, opts.outHeight), Medium))
      }
    }

  def extractForVideo(uri: URI): Future[VideoDetails] =
    metadataRetriever(uri) { implicit retriever =>
      for {
        width <- retrieve(METADATA_KEY_VIDEO_WIDTH, "video width", _.toInt)
        height <- retrieve(METADATA_KEY_VIDEO_HEIGHT, "video height", _.toInt)
        rotation <- retrieve(METADATA_KEY_VIDEO_ROTATION, "video rotation", _.toInt)
        duration <- retrieve(METADATA_KEY_DURATION, "duration", s => bp.Duration.ofMillis(s.toLong))
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

  def retrieve[A](key: Int, tag: String, convert: String => A)
                 (implicit retriever: MediaMetadataRetriever): Either[String, A] =
    for {
      s <- Option(retriever.extractMetadata(key)).toRight(s"$tag ($key) is null")
      result <- Try(convert(s)).toRight(t => s"unable to convert $tag ($key) of value '$s': ${t.getMessage}")
    } yield result

  //  def audioMetaData(asset: AssetData, entry: CacheEntry): Future[Option[Audio]] = {
//    lazy val loudness = AudioLevels(context).createAudioOverview(CacheUri(entry.data, context), asset.mime)
//      .recover{case _ => warn(s"Failed to genate loudness levels for audio asset: ${asset.id}"); None}.future
//
//    lazy val duration = MetaDataRetriever(entry.cacheFile) { r =>
//      val str = r.extractMetadata(METADATA_KEY_DURATION)
//      LoggedTry(bp.Duration.ofMillis(str.toLong)).toOption
//    }.recover{case _ => warn(s"Failed to extract duration for audio asset: ${asset.id}"); None}
//
//    asset.metaData match {
//      case Some(meta@AssetMetaData.Audio(_, Some(_))) => Future successful Some(meta) //nothing to do
//      case Some(meta@AssetMetaData.Audio(_, _)) => loudness.map { //just generate loudness
//        case Some(l) => Some(AssetMetaData.Audio(meta.duration, Some(l)))
//        case _ => Some(meta)
//      }
//      case _ => for { //no metadata - generate everything
//        l <- loudness
//        d <- duration
//      } yield d match {
//        case Some(d) => Some(AssetMetaData.Audio(d, l))
//        case _ => None
//      }
//    }
//  }

}

object MetadataServiceImpl {

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
