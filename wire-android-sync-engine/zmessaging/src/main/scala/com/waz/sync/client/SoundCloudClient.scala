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
package com.waz.sync.client

import java.net.URLEncoder

import com.waz.log.LogSE._
import com.waz.api.MediaProvider
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetData
import com.waz.model.messages.media.MediaAssetData.{MediaWithImages, Thumbnail}
import com.waz.model.messages.media.{ArtistData, MediaAssetData, PlaylistData, TrackData}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.wrappers.URI
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request, Response}
import org.json.JSONObject
import org.threeten.bp.Duration

trait SoundCloudClient {
  def resolve(soundCloudUrl: String): ErrorOr[MediaWithImages[MediaAssetData]]
  def streamingLocation(url: String): ErrorOr[URI]
}

class SoundCloudClientImpl(implicit
                           urlCreator: UrlCreator,
                           httpClient: HttpClient,
                           authRequestInterceptor: AuthRequestInterceptor) extends SoundCloudClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import SoundCloudClient._
  import Threading.Implicits.Background


  private implicit val soundCloudResponseDeserializer: RawBodyDeserializer[MediaWithImages[MediaAssetData]] =
    RawBodyDeserializer[JSONObject].map(json => SoundCloudResponse.unapply(JsonObjectResponse(json)).get)

  override def resolve(soundCloudUrl: String): ErrorOr[MediaWithImages[MediaAssetData]] = {
    Request.Get(relativePath = proxyPath("resolve", soundCloudUrl))
      .withResultType[MediaWithImages[MediaAssetData]]
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def streamingLocation(url: String): ErrorOr[URI] = {
    Request.Head(relativePath = proxyPath("stream", url))
      .withResultType[Response[Unit]]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map(
        _.right.flatMap { response =>
          response.headers.get("Location").fold2(
            Left(ErrorResponse.internalError("no location header available")),
            location => Right(URI.parse(location))
          )
        }
      )
      .future
  }

}

object SoundCloudClient {
  import com.waz.utils.JsonDecoder._

  val domainNames = Set("soundcloud.com")

  def proxyPath(resource: String, url: String) = s"/proxy/soundcloud/$resource?url=${URLEncoder.encode(url, "utf8")}"

  implicit lazy val TrackDataDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
    override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
      val (artist, artistImages) = decodeArtist(js)
      val artwork = decodeOptString('artwork_url) map decodeThumbnails
      val images = artistImages ++ artwork.toSet

      MediaWithImages(TrackData(
        provider = MediaProvider.SOUNDCLOUD,
        title = 'title,
        artist = artist,
        linkUrl = 'permalink_url,
        artwork = artwork map (_.id),
        duration = Some(Duration.ofMillis('duration)),
        streamable = 'streamable,
        streamUrl = 'stream_url,
        previewUrl = None,
        expires = MediaAssetData.defaultExpiry), images)
    }
  }

  implicit lazy val PlaylistDataDecoder: JsonDecoder[MediaWithImages[PlaylistData]] = new JsonDecoder[MediaWithImages[PlaylistData]] {
    override def apply(implicit js: JSONObject) = {
      val (artist, artistImages) = decodeArtist(js)
      val artwork = decodeOptString('artwork_url) map decodeThumbnails
      val (tracks, trackImages) = MediaAssetData.extractImageAssets(decodeSeq[MediaWithImages[TrackData]]('tracks))
      val images = artistImages ++ artwork.toSet ++ trackImages

      MediaWithImages(PlaylistData(
        provider = MediaProvider.SOUNDCLOUD,
        title = 'title,
        artist = artist,
        linkUrl = 'permalink_url,
        artwork = artwork map (_.id),
        duration = Some(Duration.ofMillis('duration)),
        tracks = tracks,
        expires = MediaAssetData.defaultExpiry), images)
    }
  }

  object SoundCloudResponse extends DerivedLogTag {
    def unapply(resp: ResponseContent): Option[MediaWithImages[MediaAssetData]] = resp match {
      case JsonObjectResponse(js) =>
        if (js has "tracks") Some(PlaylistDataDecoder(js))
        else if (js has "stream_url") Some(TrackDataDecoder(js))
        else {
          warn(l"unrecognized json for audio assets:")
          None
        }

      case other =>
        warn(l"unknown response content:")
        None
    }
  }

  private def decodeArtist(js: JSONObject): (Option[ArtistData], Set[AssetData]) = {
    Option(js.optJSONObject("user")) map { implicit user =>
      val images = decodeOptString('avatar_url) map decodeThumbnails

      (Some(ArtistData(name = 'username, avatar = images map (_.id))), images.toSet)
    } getOrElse (None, Set.empty[AssetData])
  }

  private def decodeThumbnails(url: String): AssetData = {
    def thumb(tag: String, size: Int): Thumbnail = Thumbnail(tag = tag, url = url.replaceFirst("\\-large\\.jpg", s"-$tag.jpg"), width = size, height = size)

    MediaAssetData.imageAsset(Vector(thumb("small", 32), thumb("large", 100), thumb("t500x500", 500)))
  }
}
