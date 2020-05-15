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

import android.webkit.MimeTypeMap
import com.waz.log.LogShow.SafeToLog
import com.waz.utils._

import scala.PartialFunction.cond

case class Mime(str: String) extends SafeToLog {

  lazy val extension = Mime.extensionsMap.getOrElse(this, str.drop(str.indexOf('/') + 1))
  lazy val isEmpty = str.isEmpty

  def orElse(default: => Mime) = if (isEmpty) default else this
  def orDefault = if (isEmpty) Mime.Default else this
}

object Mime {
  val Unknown = Mime("")
  val Default = Mime("application/octet-stream")
  val Text    = Mime("text/plain")

  def fromFileName(fileName: String) = extensionOf(fileName).fold2(Unknown, fromExtension)
  def fromExtension(ext: String) = Option(MimeTypeMap.getSingleton.getMimeTypeFromExtension(ext)).fold2(Unknown, Mime(_))
  def extensionOf(fileName: String): Option[String] = fileName.lastIndexOf(".") match {
    case -1 | 0 => None
    case n  => Some(fileName.substring(n + 1))
  }

  object Video {
    val MP4 = Mime("video/mp4")
    val `3GPP` = Mime("video/3gpp")
    val WebM = Mime("video/webm")

    def unapply(mime: Mime): Boolean = cond(mime) {
      case MP4 => true
      case `3GPP` => true
      case WebM => true
    }

    val supported = Set(MP4, `3GPP`, WebM)
  }

  object Image {
    val Gif     = Mime("image/gif")
    val Jpg     = Mime("image/jpeg")
    val Png     = Mime("image/png")
    val WebP    = Mime("image/webp")
    val Bmp     = Mime("image/bmp")
    val Tiff    = Mime("image/tiff")
    val Unknown = Mime("image/*")

    def unapply(mime: Mime): Boolean = supported.contains(mime)

    val supported = Set(Gif, Jpg, Png, WebP, Bmp)
  }

  object Audio {
    val MP3 = Mime("audio/mp3")
    val MPEG3 = Mime("audio/mpeg3")
    val MPEG = Mime("audio/mpeg")
    val MP4 = Mime("audio/mp4")
    val M4A = Mime("audio/x-m4a")
    val AAC = Mime("audio/aac")
    val `3GPP` = Mime("audio/3gpp")
    val AMR_NB = Mime("audio/amr-nb")
    val AMR_WB = Mime("audio/amr-wb")
    val Ogg = Mime("audio/ogg")
    val FLAC = Mime("audio/flac")
    val WAV = Mime("audio/wav")
    val PCM = Mime("audio/pcm-s16le;rate=44100;channels=1")

    def unapply(mime: Mime): Boolean = mime.str.startsWith("audio/")//supported(mime)

    val supported = Set(MP3, MPEG3, MPEG, MP4, M4A, AAC, `3GPP`, AMR_NB, AMR_WB, Ogg, FLAC, WAV)
  }

  val extensionsMap = Map(
    Video.MP4    -> "mp4",
    Video.`3GPP` -> "3gpp",
    Video.WebM   -> "webm",
    Image.Gif    -> "gif",
    Image.Jpg    -> "jpg",
    Image.Png    -> "png",
    Image.WebP   -> "webp",
    Image.Bmp    -> "bmp",
    Image.Tiff   -> "tiff",
    Audio.MP3    -> "mp3",
    Audio.MPEG3  -> "mpeg3",
    Audio.MPEG   -> "mpeg",
    Audio.MP4    -> "mp4",
    Audio.M4A    -> "m4a",
    Audio.AAC    -> "aac",
    Audio.`3GPP` -> "3gpp",
    Audio.AMR_NB -> "amr",
    Audio.AMR_WB -> "amr",
    Audio.Ogg    -> "ogg",
    Audio.FLAC   -> "flac",
    Audio.WAV    -> "wav",
    Audio.PCM    -> "m4a",
    Text         -> "txt"
  )
}
