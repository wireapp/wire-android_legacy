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
package com.waz.model.messages.media

import com.waz.api.{KindOfMedia, MediaProvider, Message}
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model._
import com.waz.utils.wrappers.URI
import org.threeten.bp.{Duration, Instant}

import scala.concurrent.duration._

sealed trait MediaAssetData {
  def kind: KindOfMedia
  def provider: MediaProvider
  def title: String
  def artist: Option[ArtistData]
  def duration: Option[Duration]
  def linkUrl: String
  def artwork: Option[GeneralAssetId]
  def tracks: Vector[TrackData]
  def expires: Instant

  def hasExpired: Boolean = Instant.now isAfter expires
}

case class TrackData(provider: MediaProvider, title: String, artist: Option[ArtistData], linkUrl: String, artwork: Option[GeneralAssetId], duration: Option[Duration], streamable: Boolean, streamUrl: Option[String], previewUrl: Option[String], expires: Instant) extends MediaAssetData {
  val kind = KindOfMedia.TRACK

  def tracks: Vector[TrackData] = Vector(this)
}

case class PlaylistData(provider: MediaProvider, title: String, artist: Option[ArtistData], linkUrl: String, artwork: Option[GeneralAssetId], duration: Option[Duration], tracks: Vector[TrackData], expires: Instant) extends MediaAssetData {
  val kind = KindOfMedia.PLAYLIST
}

case class EmptyMediaAssetData(provider: MediaProvider) extends MediaAssetData {
  val kind = KindOfMedia.UNKNOWN
  val title = ""
  val artist = None
  val linkUrl = ""
  val artwork = None
  val duration = None
  val expires = Instant.EPOCH
  val tracks = Vector.empty
}

case class ArtistData(name: String, avatar: Option[AssetId])

object MediaAssetData {
  import com.waz.utils._

  case class MediaWithImages[+T <: MediaAssetData](media: T, images: Set[AssetData])

  case class Thumbnail(tag: String, url: String, width: Int, height: Int)

  val DefaultExpiryTime = 7.days
  def defaultExpiry: Instant = Instant.now plus DefaultExpiryTime
  def expiryAfter(d: FiniteDuration) = Instant.now plus d

  def empty(partType: Message.Part.Type): MediaAssetData = EmptyMediaAssetData(partType match {
    case Message.Part.Type.SOUNDCLOUD => MediaProvider.SOUNDCLOUD
    case Message.Part.Type.SPOTIFY => MediaProvider.SPOTIFY
    case _ => MediaProvider.YOUTUBE
  })

  implicit lazy val KindOfMediaCodec: EnumCodec[KindOfMedia, String] = EnumCodec.injective {
    case KindOfMedia.PLAYLIST => "playlist"
    case KindOfMedia.TRACK => "track"
    case KindOfMedia.UNKNOWN => "unknown"
  }

  implicit lazy val MediaProviderCodec: EnumCodec[MediaProvider, String] = EnumCodec.injective {
    case MediaProvider.SOUNDCLOUD => "soundcloud"
    case MediaProvider.SPOTIFY => "spotify"
    case MediaProvider.YOUTUBE => "youtube"
  }

  def imageAsset(thumbs: Vector[Thumbnail]): AssetData = {
    val orig = thumbs.lastOption

    AssetData(
      mime = Mime.Image.Jpg,
      metaData = orig.map(o => AssetMetaData.Image(Dim2(o.width, o.height), Tag(o.tag))),
      source = orig.map(o => URI.parse(o.url)))
  }

  def extractImageAssets[T <: MediaAssetData](src: Vector[MediaWithImages[T]]) = src.foldLeft((Vector.empty[T], Set.empty[AssetData])) { case ((tracks, images), MediaWithImages(track, image)) => (tracks :+ track, images ++ image) }
}
