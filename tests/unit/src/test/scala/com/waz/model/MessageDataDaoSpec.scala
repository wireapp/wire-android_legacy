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

import com.waz.api.{MediaProvider, Message}
import com.waz.model.messages.media.{MediaAssetData, TrackData}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.utils.wrappers.URI
import com.waz.utils._
import org.json.JSONArray
import org.scalatest._
import org.threeten.bp
import org.threeten.bp.Instant

class MessageDataDaoSpec extends AndroidFreeSpec {

  val knockUser = UserId()

  feature("MessageContent decoding") {

    lazy val assetId = AssetId()
    lazy val now = Instant.now

    val simpleMessageContent = MessageContent(Message.Part.Type.TEXT, "text content")
    val simpleMessageJson = Json("type" -> "Text", "content" -> "text content")

    val contentWithMention = MessageContent(Message.Part.Type.TEXT, "text content @user", mentions = Seq(Mention(Some(knockUser), 13, 5)))
    val jsonWithMention =
      Json(
        "type" -> "Text",
        "content" -> "text content @user",
        "mentions" -> Json(Seq(Map("user_id" -> knockUser.str, "start" -> 13, "length" -> 5)))
      )

    val complexMesageContent =
      MessageContent(
        Message.Part.Type.YOUTUBE,
        "youtube link",
        richMedia = Option[MediaAssetData](TrackData(MediaProvider.YOUTUBE, "title", None, "link-url", None, Some(bp.Duration.ofMillis(123L)), streamable = true, None, Some("preview-url"), now)),
        openGraph = Some(OpenGraphData("wire", "descr", Some(URI.parse("http://www.wire.com")), "website", None)),
        Some(assetId),
        100,
        80,
        syncNeeded = true,
        mentions = Nil
      )
    val complexMessageJson =
      Json(
        "type" -> "YouTube",
        "content" -> "youtube link",
        "richMedia" -> Json(
          "kind" -> "track",
          "provider" -> "youtube",
          "title" -> "title",
          "linkUrl" -> "link-url",
          "durationMillis" -> 123,
          "streamable" -> true,
          "previewUrl" -> "preview-url",
          "expires" -> now.toEpochMilli
        ),
        "openGraph" -> Json("title" -> "wire", "description" -> "descr", "image" -> "http://www.wire.com", "tpe" -> "website"),
        "asset" -> assetId.str,
        "width" -> 100,
        "height" -> 80,
        "syncNeeded" -> true
      )

    val contents =
      Seq(
        simpleMessageJson -> simpleMessageContent,
        jsonWithMention -> contentWithMention,
        complexMessageJson -> complexMesageContent
      )

    scenario("Decode message content") {
      contents foreach {
        case (json, ct) =>
          withClue(json.toString) {
            JsonDecoder.decode[MessageContent](json.toString) shouldEqual ct
          }
      }
    }

    scenario("Decode mentions") {
      val encoded = JsonEncoder.encode(contentWithMention)
      println(encoded.toString)
      val decoded = JsonDecoder.decode[MessageContent](encoded.toString)
      decoded shouldEqual contentWithMention
    }
  }

}
