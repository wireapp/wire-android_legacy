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
package com.waz.service.media

import com.waz.api.{MediaProvider, Message}
import com.waz.api.Message.Part
import com.waz.api.impl.ErrorResponse
import com.waz.model.messages.media.MediaAssetData.MediaWithImages
import com.waz.model.messages.media.TrackData
import com.waz.model._
import com.waz.service.assets.{AES_CBC_Encryption, AssetService, Content, ContentForUpload, DetailsNotReady, PreviewNotReady, UploadAsset, UploadAssetStatus}
import com.waz.service.assets2._
import com.waz.service.messages.MessagesService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.client.YouTubeClient
import com.waz.utils.wrappers.URI
import org.threeten.bp.Instant

import scala.concurrent.Future

class YouTubeMediaServiceSpec extends AndroidFreeSpec {

  val client = mock[YouTubeClient]
  val assets = mock[AssetService]
  val messages = mock[MessagesService]

  val url = "http://youtube.com/watch?v=c0KYU2j0TM4"
  val videoId = "c0KYU2j0TM4"
  val previewUrl = "https://i.ytimg.com/vi/ZtYTwUxhAoI/maxresdefault.jpg"

  scenario("Previews are stored locally and message data is updated appropriately") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))
    val trackTitle = "test"
    val retention = Retention.Eternal
    val trackData = TrackData(MediaProvider.YOUTUBE, trackTitle, None, "", None, None,
      streamable = false, None, None, Instant.now)
    val uri = URI.parse(previewUrl)
    val mime = Mime.Video.WebM
    val images = Set(AssetData.Empty.copy(source = Some(uri), mime = mime))
    val media = MediaWithImages(trackData, images)
    val previewBytes = Array.emptyByteArray
    val contentForUpload = ContentForUpload(trackTitle, Content.Bytes(mime, previewBytes))
    val previewAsset = UploadAsset(UploadAssetId("previewId"), None, trackTitle, Sha256.Empty,
      MD5.apply(previewBytes), mime, PreviewNotReady, 0, 0, retention, public = false,
      AES_CBC_Encryption.random, None, DetailsNotReady, UploadAssetStatus.NotStarted, None)

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Right(media)))
    (client.loadPreview _).expects(uri).once().returning(Future.successful(Right(previewBytes)))
    (messages.retentionPolicy2ById _).expects(*).once().returning(Future.successful(retention))
    (assets.createAndSaveUploadAsset _).expects(contentForUpload, *, *, *, None).returning(Future.successful(previewAsset))

    val newMedia = trackData.copy(artwork = Some(previewAsset.id))
    val expectedContent = content.copy(tpe = Message.Part.Type.YOUTUBE, richMedia = Some(newMedia))

    result(service.updateMedia(msgData, content)) shouldEqual Right(expectedContent)
  }

  scenario("Previews that fail to load fall back to text message type") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))
    val trackData = TrackData(MediaProvider.YOUTUBE, "test", None, "", None, None,
      streamable = false, None, None, Instant.now)
    val uri = URI.parse(previewUrl)
    val images = Set(AssetData.Empty.copy(source = Some(uri), mime = Mime.Video.WebM))
    val media = MediaWithImages(trackData, images)

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Right(media)))
    (client.loadPreview _).expects(uri).once().returning(Future.successful(Left(ErrorResponse.InternalError)))

    val expectedContent = content.copy(tpe = Message.Part.Type.TEXT)

    result(service.updateMedia(msgData, content)) shouldEqual Right(expectedContent)
  }

  scenario("Previews that fail to load non-fatally don't fall back to text") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))
    val trackData = TrackData(MediaProvider.YOUTUBE, "test", None, "", None, None,
      streamable = false, None, None, Instant.now)
    val uri = URI.parse(previewUrl)
    val images = Set(AssetData.Empty.copy(source = Some(uri), mime = Mime.Video.WebM))
    val media = MediaWithImages(trackData, images)
    val errorResponse = ErrorResponse(ErrorResponse.ConnectionErrorCode, "connection-error", "")

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Right(media)))
    (client.loadPreview _).expects(uri).once().returning(Future.successful(Left(errorResponse)))

    result(service.updateMedia(msgData, content)) shouldEqual Left(errorResponse)
  }

  scenario("videos that fail to load fall back to text message type") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Left(ErrorResponse.InternalError)))

    val expectedContent = content.copy(tpe = Message.Part.Type.TEXT)

    result(service.updateMedia(msgData, content)) shouldEqual Right(expectedContent)
  }

  scenario("Videos that fail to load non-fatally don't fall back to text") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))
    val errorResponse = ErrorResponse(ErrorResponse.ConnectionErrorCode, "connection-error", "")

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Left(errorResponse)))

    result(service.updateMedia(msgData, content)) shouldEqual Left(errorResponse)
  }


  scenario("invalid video urls fall back to text message type") {
    val service = getService

    val content = MessageContent(Part.Type.YOUTUBE, url, None, None, None, 5, 5,
      syncNeeded = false, Seq())
    val msgData = MessageData.Empty.copy(content = Seq(content))

    (client.loadVideo _).expects(videoId).once().returning(Future.successful(Left(ErrorResponse.InternalError)))

    val expectedContent = content.copy(tpe = Message.Part.Type.TEXT)

    result(service.updateMedia(msgData, content)) shouldEqual Right(expectedContent)
  }


  def getService = new YouTubeMediaService(client, assets, messages)
}
