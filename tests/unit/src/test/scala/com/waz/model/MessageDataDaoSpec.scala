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

import android.content.ContentValues
import com.waz.api.{MediaProvider, Message}
import com.waz.db.ZMessagingDB
import com.waz.model.messages.media.{MediaAssetData, TrackData}
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.utils.wrappers.{DB, URI}
import com.waz.utils._
import org.json.JSONArray
import org.robolectric.Robolectric
import org.scalatest._
import org.threeten.bp
import org.threeten.bp.Instant

@Ignore class MessageDataDaoSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  lazy val dbHelper = new ZMessagingDB(Robolectric.application, "dbName")

  val convId = ConvId()
  val knockUser = UserId()

  val events = List(
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(1)),
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(2)),
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(3)),
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(4)),
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(5)),
    MessageData(MessageId(), convId, Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(5)),
    MessageData(MessageId(), convId, Message.Type.KNOCK, knockUser, time = RemoteInstant.ofEpochMilli(6)),
    MessageData(MessageId(), ConvId(), Message.Type.TEXT, UserId(), time = RemoteInstant.ofEpochMilli(7))
  )

  after {
    dbHelper.close()
  }

  implicit def db: DB = dbHelper.getWritableDatabase
  import com.waz.model.MessageData.MessageDataDao._

  scenario("find messages returns events ordered by time") {
    insertOrReplace(events)
    list(findMessages(convId)) shouldEqual events.filter(_.convId == convId).sortBy(_.time)
  }

  feature("MessageContent decoding") {

    lazy val assetId = AssetId()
    lazy val now = Instant.now

    lazy val contents = Seq(
      Json(
        "type" -> "Text",
        "content" -> "text content"
      ) -> MessageContent(Message.Part.Type.TEXT, "text content"),
      Json(
        "type" -> "Text",
        "content" -> "text content @user",
        "mentions" -> Json(knockUser.str -> "user")
      ) -> MessageContent(Message.Part.Type.TEXT, "text content @user", mentions = Map(knockUser -> "user")),
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
      ) -> MessageContent(
            Message.Part.Type.YOUTUBE, "youtube link",
            richMedia = Option[MediaAssetData](TrackData(MediaProvider.YOUTUBE, "title", None, "link-url", None, Some(bp.Duration.ofMillis(123L)), streamable = true, None, Some("preview-url"), now)),
            openGraph = Some(OpenGraphData("wire", "descr", Some(URI.parse("http://www.wire.com")), "website", None)), Some(assetId), 100, 80, syncNeeded = true, mentions = Map.empty[UserId, String])
    )

    scenario("Decode message content") {
      contents foreach {
        case (json, ct) =>
          withClue(json.toString) {
            JsonDecoder.decode[MessageContent](json.toString) shouldEqual ct
          }
      }
    }

    scenario("Decode message with sample content json") {
      val json = returning(new JSONArray)(arr => contents.foreach(i => arr.put(i._1)))

      val msg = MessageData(MessageId(), convId, Message.Type.RICH_MEDIA, UserId())
      insertOrReplace(msg)

      getById(msg.id) shouldEqual Some(msg)

      val values = new ContentValues()
      values.put(Content.name, json.toString)
      db.update(table.name, values, s"${Id.name} = ?", Array(msg.id.str))

      getById(msg.id) shouldEqual Some(msg.copy(content = contents.map(_._2)))
    }
  }
}
