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
package org.robolectric.shadows

import java.io.{File, FileInputStream, InputStream}

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.coremedia.iso.IsoFile
import com.coremedia.iso.boxes.{Box, TrackBox}
import com.googlecode.mp4parser.FileDataSourceImpl
import com.googlecode.mp4parser.util.{Matrix, Path}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.IoUtils
import org.robolectric.annotation.{Implementation, Implements, Resetter}

import scala.collection.{JavaConverters, mutable}
import scala.concurrent.duration._

@Implements(classOf[MediaMetadataRetriever])
object ShadowMediaMetadataRetriever2 {
  private val metadata = new mutable.HashMap[String, mutable.HashMap[Int, String]]
  private val frames = new mutable.HashMap[String, mutable.HashMap[Long, Bitmap]]

  def addMetadata(path: String, keyCode: Int, value: String) =
    metadata.getOrElseUpdate(path, new mutable.HashMap[Int, String]).put(keyCode, value)

  def addFrame(path: String, time: Long, bitmap: Bitmap): Unit =
    frames.getOrElseUpdate(path, new mutable.HashMap[Long, Bitmap]).put(time, bitmap)

  def addFrame(context: Context, uri: Uri, time: Long, bitmap: Bitmap): Unit = addFrame(uri.toString, time, bitmap)

  @Resetter def reset() = {
    metadata.clear
    frames.clear
  }
}

@Implements(classOf[MediaMetadataRetriever])
class ShadowMediaMetadataRetriever2 {
  import ShadowMediaMetadataRetriever2._

  private var meta = Map.empty[Int, String]
  private var frame = Map.empty[Long, Bitmap]

  private def getMeta(stream: => InputStream) = try {
    import JavaConverters._

    val file = File.createTempFile("media", ".mp4")
    file.deleteOnExit()
    IoUtils.copy(stream, file)

    val isoFile = new IsoFile(new FileDataSourceImpl(file))

    val duration = Option(isoFile.getMovieBox).flatMap(b => Option(b.getMovieHeaderBox)).map { mb =>
      (mb.getDuration * 1000 / mb.getTimescale).millis
    } .getOrElse(Duration.Zero)

    val trackHeaderBox = Path.getPaths[Box](isoFile, "/moov/trak/").asScala collectFirst {
      case tb: TrackBox if tb.getTrackHeaderBox.getWidth > 0 && tb.getTrackHeaderBox.getHeight > 0 => tb.getTrackHeaderBox
    }

    val dimens = trackHeaderBox map { tb =>
      val rotation = tb.getMatrix match {
        case Matrix.ROTATE_90   => 90
        case Matrix.ROTATE_270  => 270
        case Matrix.ROTATE_180  => 180
        case _                  => 0
      }
      (tb.getWidth.toInt, tb.getHeight.toInt, rotation)
    }

    ZLog.debug(s"getMeta, duration: $duration, dimens: $dimens")

    Map(
      MediaMetadataRetriever.METADATA_KEY_DURATION -> duration.toMillis.toString,
      MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH -> dimens.fold("0")(_._1.toString),
      MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT -> dimens.fold("0")(_._2.toString),
      MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION -> dimens.fold("0")(_._3.toString)
    )
  } finally {
    stream.close()
  }

  @Implementation def setDataSource(path: String): Unit = {
    ZLog.debug(s"getMeta, setDataSource $path")
    meta = metadata.getOrElse(path, getMeta(new FileInputStream(path))).toMap
    frame = frames.getOrElse(path, Map.empty).toMap
  }

  @Implementation def setDataSource(context: Context, uri: Uri): Unit = {
    ZLog.debug(s"getMeta, setDataSource $uri")
    meta = metadata.getOrElse(uri.toString, getMeta(context.getContentResolver.openInputStream(uri))).toMap
    frame = frames.getOrElse(uri.toString, Map.empty).toMap
  }
  @Implementation def extractMetadata(keyCode: Int): String = meta.get(keyCode).orNull

  @Implementation def getFrameAtTime(timeUs: Long, option: Int): Bitmap = frame.get(timeUs).orNull
}
