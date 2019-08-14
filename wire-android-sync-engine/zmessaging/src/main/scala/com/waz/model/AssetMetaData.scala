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
package com.waz.model

import java.io.{File, FileInputStream, InputStream}

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever._
import android.net.Uri
import com.waz.log.LogSE._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.assets.MetaDataRetriever
import com.waz.utils.wrappers.URI
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.JSONObject
import org.threeten.bp
import org.threeten.bp.Duration

import scala.concurrent.Future
import scala.util.Try

sealed abstract class AssetMetaData(val jsonTypeTag: Symbol)
object AssetMetaData {

  implicit lazy val AssetMetaDataEncoder: JsonEncoder[AssetMetaData] = new JsonEncoder[AssetMetaData] {
    override def apply(data: AssetMetaData): JSONObject = JsonEncoder { o =>
      o.put("type", data.jsonTypeTag.name)
      data match {
        case Empty => // nothing to add
        case Video(dimensions, duration) =>
          o.put("dimensions", JsonEncoder.encode(dimensions))
          o.put("duration", duration.toMillis)
        case Audio(duration, loudness) =>
          o.put("duration", duration.toMillis)
          loudness.foreach(l => o.put("levels", JsonEncoder.arrNum(l.levels)))
        case Image(dimensions, tag) =>
          o.put("dimensions", JsonEncoder.encode(dimensions))
          o.put("tag", tag)
      }
    }
  }

  implicit lazy val AssetMetaDataDecoder: JsonDecoder[AssetMetaData] = new JsonDecoder[AssetMetaData] {
    import JsonDecoder._

    override def apply(implicit o: JSONObject): AssetMetaData = decodeSymbol('type) match {
      case 'empty => Empty
      case 'video =>
        Video(opt[Dim2]('dimensions).getOrElse(Dim2(0, 0)), Duration.ofMillis('duration))
      case 'audio =>
        Audio(Duration.ofMillis('duration), decodeOptLoudness('levels))
      case 'image =>
        Image(JsonDecoder[Dim2]('dimensions), Image.Tag(decodeString('tag)))
      case other =>
        throw new IllegalArgumentException(s"unsupported meta data type: $other")
    }
  }

  trait HasDuration {
    val duration: Duration
  }
  object HasDuration {
    def unapply(meta: HasDuration): Option[Duration] = Some(meta.duration)
  }

  trait HasDimensions {
    val dimensions: Dim2
  }
  object HasDimensions {
    def unapply(meta: HasDimensions): Option[Dim2] = Some(meta.dimensions)
  }

  case class Loudness(levels: Vector[Float])

  case class Video(dimensions: Dim2, duration: Duration) extends AssetMetaData('video) with HasDimensions with HasDuration
  case class Image(dimensions: Dim2, tag: Image.Tag = Image.Tag.Empty) extends AssetMetaData('image) with HasDimensions
  case class Audio(duration: Duration, loudness: Option[Loudness] = None) extends AssetMetaData('audio) with HasDuration
  case object Empty extends AssetMetaData('empty)

  object Video extends DerivedLogTag {
    def apply(file: File): Future[Either[String, Video]] = MetaDataRetriever(file)(apply)

    def apply(context: Context, uri: Uri): Future[Either[String, Video]] = MetaDataRetriever(context, uri)(apply)

    def apply(retriever: MediaMetadataRetriever): Either[String, Video] = {
      def retrieve[A](k: Int, tag: String, convert: String => A) =
        Option(retriever.extractMetadata(k)).toRight(s"$tag ($k) is null")
          .flatMap(s => Try(convert(s)).toRight(t => s"unable to convert $tag ($k) of value '$s': ${t.getMessage}"))

      for {
        width    <- retrieve(METADATA_KEY_VIDEO_WIDTH, "video width", _.toInt)
        height   <- retrieve(METADATA_KEY_VIDEO_HEIGHT, "video height", _.toInt)
        rotation <- retrieve(METADATA_KEY_VIDEO_ROTATION, "video rotation", _.toInt)
        dim       = if (rotation / 90 % 2 == 0) Dim2(width, height) else Dim2(height, width)
        duration <- retrieve(METADATA_KEY_DURATION, "duration", s => bp.Duration.ofMillis(s.toLong))
      } yield {
        verbose(l"width: $width, height: $height, rotation: $rotation, dim: $dim, duration: $duration")
        AssetMetaData.Video(dim, duration)
      }
    }
  }

  object Image {

    sealed abstract class Tag(str: String) {
      override def toString: String = str
    }

    /**
      * An implementation note on Image tags:
      * V2 images often contain both a "preview" and a "medium" version, where we rely on the tag to drop the preview version
      * V3 images can also contain tags, but no client currently sends a "preview" version, so we don't need to worry.
      *
      * V2 profile pictures are stored in the "picture" field of user data with the tags "smallProfile" and "medium". The
      * Webapp team requires that we always upload a small version of the image, as they don't have their own caching (yet)
      * V3 profile pictures are stored in the "assets" field of user data with the tags "preview" or "complete". Again we
      * must upload both for webapp.
      *
      * To simplify implementations, I'm going with two internal tags, "preview" and "medium". Depending on where they're
      * used though, they may be translated into "smallProfile" or "complete"
      *
      * TODO Dean: it would be nice to sync up with other clients and steadily introduce a more uniform set of tags
      */

    object Tag {
      case object Preview      extends Tag("preview")
      case object Medium       extends Tag("medium")
      case object Empty        extends Tag("")

      def apply(tag: String): Tag = tag match {
        case "preview"      => Preview
        case "smallProfile" => Preview
        case "medium"       => Medium
        case "complete"     => Medium
        case _              => Empty
      }
    }

    val Empty = Image(Dim2.Empty, Tag.Empty)

    def apply(file: File): Option[Image] = apply(file, Tag.Empty)
    def apply(file: File, tag: Tag): Option[Image] = apply(new FileInputStream(file), tag)

    def apply(context: Context, uri: URI): Option[Image] = apply(context, uri, Tag.Empty)
    def apply(context: Context, uri: URI, tag: Tag): Option[Image] = apply(context.getContentResolver.openInputStream(URI.unwrap(uri)), tag)

    def apply(stream: => InputStream, tag: Tag): Option[Image] = Try(Managed(stream).acquire { is =>
      val opts = new BitmapFactory.Options
      opts.inJustDecodeBounds = true
      BitmapFactory.decodeStream(is, null, opts)
      if (opts.outWidth == 0 && opts.outHeight == 0) None
      else Some(Image(Dim2(opts.outWidth, opts.outHeight), tag))
    }).getOrElse(Some(Image(Dim2.Empty, tag)))
  }
}
