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

import java.net.URL

import com.waz.log.LogSE._
import com.waz.api.MediaProvider
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetData
import com.waz.model.messages.media.MediaAssetData.{MediaWithImages, Thumbnail}
import com.waz.model.messages.media._
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.wrappers.URI
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Method, RawBodyDeserializer, Request}
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

// TODO at some point, we probably should support paging of long playlists (> 50 items), too, but for now, let's just work on supporting playlists at all

trait YouTubeClient {
  def loadVideo(id: String): ErrorOr[MediaWithImages[TrackData]]
  def loadPreview(uri: URI): ErrorOr[Array[Byte]]
  def loadPlaylist(id: String): ErrorOr[MediaWithImages[PlaylistData]]
}

class YouTubeClientImpl(implicit
                        urlCreator: UrlCreator,
                        httpClient: HttpClient,
                        authRequestInterceptor: AuthRequestInterceptor) extends YouTubeClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import Threading.Implicits.Background
  import YouTubeClient._

  private implicit val trackDataDeserializer: RawBodyDeserializer[MediaWithImages[TrackData]] =
    RawBodyDeserializer[JSONObject].map(json => TrackResponse.unapply(JsonObjectResponse(json)).get)

  override def loadVideo(id: String): ErrorOr[MediaWithImages[TrackData]] = {
    Request.Get(relativePath = resourcePath("videos"), queryParameters = List("part" -> "snippet", "id" -> id))
      .withResultType[MediaWithImages[TrackData]]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def loadPreview(uri: URI): ErrorOr[Array[Byte]] = {
    Request.create(method = Method.Get, url = new URL(uri.toString))
      .withResultType[Array[Byte]]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  private implicit val playlistDataDeserializer: RawBodyDeserializer[MediaWithImages[PlaylistData]] =
    RawBodyDeserializer[JSONObject].map(json => PlaylistResponse.unapply(JsonObjectResponse(json)).get)

  private implicit val playlistItemsDeserializer: RawBodyDeserializer[(Vector[TrackData], Set[AssetData])] =
    RawBodyDeserializer[JSONObject].map(json => PlaylistItemsResponse.unapply(JsonObjectResponse(json)).get)

  override def loadPlaylist(id: String): ErrorOr[MediaWithImages[PlaylistData]] = {
    val playlistResponse =
      Request.Get(
        relativePath = resourcePath("playlists"),
        queryParameters = List("part" -> "snippet", "id" -> id)
      )
      .withResultType[MediaWithImages[PlaylistData]]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .future

    val itemsResponse =
      Request.Get(
        relativePath = resourcePath("playlistItems"),
        queryParameters = queryParameters("part" -> "snippet", "playlistId" -> id, "maxResults" -> 50)
      )
      .withResultType[(Vector[TrackData], Set[AssetData])]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .future

    for {
      playlistOrError <- playlistResponse
      itemsOrError <- itemsResponse
    } yield for {
      playlist <- playlistOrError
      tracksWithImages <- itemsOrError
    } yield playlist.copy(
      media = playlist.media.copy(tracks = tracksWithImages._1),
      images = playlist.images ++ tracksWithImages._2
    )
  }

}

object YouTubeClient extends DerivedLogTag {

  val DomainNames = Set("youtube.com", "youtu.be")
  val Base = "/proxy/youtube/v3"

  def resourcePath(resource: String) = s"$Base/$resource"

  import JsonDecoder._

  case class Snippet(title: String, artist: Option[ArtistData], thumbs: Option[Vector[Thumbnail]], playlistId: Option[String], position: Option[Int], videoId: Option[String])

  def thumb(tag: String)(implicit js: JSONObject) = Thumbnail(tag, 'url, 'width, 'height)

  lazy val SnippetDecoder: JsonDecoder[Snippet] = new JsonDecoder[Snippet] {
    override def apply(implicit js: JSONObject) = Snippet(
      title = 'title,
      artist = decodeOptString('channelTitle) map { ArtistData(_, avatar = None) },
      thumbs = decodeThumbnails(js),
      playlistId = decodeOptString('playlistId),
      position = 'position,
      videoId = Option(js.optJSONObject("resourceId")) flatMap { res => Option(res.optString("videoId")) })
  }

  object TrackResponse {
    def unapply(response: ResponseContent): Option[MediaWithImages[TrackData]] =
      parse[TrackData](response, "youtube#videoListResponse")(TrackDecoder)
        .flatMap { case (media, assets) => media.headOption map (t => MediaWithImages(t, assets)) }

    lazy val TrackDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val url = uri("https://www.youtube.com/watch")(_ :? ("v", decodeString('id))).toString
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(TrackData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = url,
          artwork = asset map (_.id),
          duration = None,
          streamable = true,
          streamUrl = Some(url),
          previewUrl = None,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  object PlaylistResponse {
    def unapply(response: ResponseContent): Option[MediaWithImages[PlaylistData]] = parse[PlaylistData](response, "youtube#playlistListResponse")(PlaylistDecoder) .flatMap { case (media, assets) => media.headOption map (t => MediaWithImages(t, assets)) }

    lazy val PlaylistDecoder: JsonDecoder[MediaWithImages[PlaylistData]] = new JsonDecoder[MediaWithImages[PlaylistData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[PlaylistData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(PlaylistData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = s"https://www.youtube.com/playlist?list=${decodeString('id)}",
          artwork = asset map (_.id),
          duration = None,
          tracks = Vector.empty,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  object PlaylistItemsResponse {
    def unapply(response: ResponseContent): Option[(Vector[TrackData], Set[AssetData])] = parse[TrackData](response, "youtube#playlistItemListResponse")(TrackDecoder)

    lazy val TrackDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val url = uri("https://www.youtube.com/watch") { _ :? ("list", snippet.playlistId) :& ("index", snippet.position map (_.toString)) :& ("v", snippet.videoId) } .toString
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(TrackData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = url,
          artwork = asset map (_.id),
          duration = None,
          streamable = true,
          streamUrl = Some(url),
          previewUrl = None,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  private def parse[T <: MediaAssetData](response: ResponseContent, kind: String)(implicit dec: JsonDecoder[MediaWithImages[T]]): Option[(Vector[T], Set[AssetData])] = try {
    response match {
      case JsonObjectResponse(js) if Option(js.optString("kind")).contains(kind) && js.has("items") =>
        Some(MediaAssetData.extractImageAssets(arrayColl[MediaWithImages[T], Vector](js.getJSONArray("items"))))
      case resp =>
        warn(l"unknown response content:")
        None
    }
  } catch {
    case NonFatal(e) =>
      warn(l"response: parsing failed", e)
      None
  }

  private def decodeThumbnails(js: JSONObject): Option[Vector[Thumbnail]] = Option(js.optJSONObject("thumbnails")) map { thumbsJs =>
    thumbsJs.keys().asInstanceOf[java.util.Iterator[String]].asScala.toVector map { key => thumb(key)(thumbsJs.getJSONObject(key)) }
  } map (_ sortBy (_.width))
}
