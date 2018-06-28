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

import com.waz.model.ConversationData.ConversationType
import com.waz.model.Event
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.threading.CancellableFuture
import com.waz.utils.IoUtils.asString
import com.waz.utils._
import com.waz.znet.Response.Status.Success
import com.waz.znet.Response._
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet._
import org.json.JSONObject
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.threeten.bp.Instant

@Ignore class ConversationClientSpec extends FeatureSpec with Matchers with ScalaFutures with BeforeAndAfterAll with RobolectricTests {

  feature("json parsing") {

    scenario("parse small sample response") {
      val json = readResource("/conv/resp1.json")

      val responses = JsonDecoder.array[ConversationResponse](json.getJSONArray("conversations"))
      info(s"$responses")

      responses.length shouldEqual 3
      // TODO: assert values
    }

    scenario("parse big sample response") {
      val json = readResource("/conv/resp2.json")

      val responses = JsonDecoder.array[ConversationResponse](json.getJSONArray("conversations"))
      responses.count(_.convType == ConversationType.Self) shouldEqual 1
      //TODO: assert
    }

    scenario("parse sample response 3") {
      val json = readResource("/conv/resp3.json")

      val responses = JsonDecoder.array[ConversationResponse](json.getJSONArray("conversations"))
      responses.count(_.convType == ConversationType.Self) shouldEqual 1
      responses should have size 108
      val conv = responses.find(_.name.contains("Simon Tam"))
      conv should be('defined)
    }

    scenario("parse events 1") {
      val json = readResource("/conv/conv_events1.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }

    scenario("parse events 2") {
      val json = readResource("/conv/conv_events2.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }

    scenario("parse events 3") {
      val json = readResource("/conv/conv_events3.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }

    scenario("parse events 4") {
      val json = readResource("/conv/conv_events4.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }

    scenario("parse events 6") {
      val json = readResource("/conv/conv_events6.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }

    scenario("parse events 7") {
      val json = readResource("/conv/conv_events7.json")
      val events = JsonDecoder.array[Event](json.getJSONArray("events"))
    }
  }

  feature("response extractor") {

    scenario("parse conversation status from response") {

      val json =
        """{
          |  "access":["private"],
          |  "creator":"5604c40a-4fec-454f-a388-e3bbe1bb6e5b",
          |  "members":{
          |    "self":{
          |      "status":0,
          |      "last_read":"1d.800122000a52f34d",
          |      "muted_time":null,
          |      "otr_muted_ref":null,
          |      "muted":null,
          |      "status_time":"2015-04-13T10:26:12.851Z",
          |      "status_ref":"0.0",
          |      "id":"5604c40a-4fec-454f-a388-e3bbe1bb6e5b",
          |      "otr_archived":false,
          |      "cleared":null,
          |      "otr_muted":false,
          |      "otr_archived_ref":null,
          |      "archived":"1d.800122000a52f34d"
          |    },
          |    "others":[{"status":0,"id":"ab4b1b9b-5874-4159-a322-3093e4db850d"}]
          |  },
          |  "name":"Foo Bar",
          |  "id":"014fdfa5-a860-46a8-86ab-144ec696f368",
          |  "type":2,
          |  "last_event_time":"2015-09-03T10:28:08.992Z",
          |  "last_event":"1d.800122000a52f34d"
          |}""".stripMargin

      val resp = ConversationResponse.Decoder(new JSONObject(json))
      resp.archived shouldEqual false
      resp.archivedTime shouldEqual Instant.EPOCH
      resp.muted shouldEqual false
      resp.mutedTime shouldEqual Instant.EPOCH
    }

    scenario("extract single ConversationResponse") {
      val json = readResource("/conv/resp1.json")

      JsonObjectResponse(json.getJSONArray("conversations").getJSONObject(0)) match {
        case ConversationsResult(Seq(conversation), false) => info(s"parsed conversation: $conversation")
        case _ => fail("conversation response didn't match")
      }
    }
    
    scenario("extract conversations list response") {
      val json = readResource("/conv/resp1.json")

      JsonObjectResponse(json) match {
        case ConversationsResult(conversations, false) =>
          info(s"got conversations: $conversations")
          conversations.length shouldEqual 3
        case _ => fail("didn't match conversations response")
      }

    }
  }

  def readResource(path: String) = new JSONObject(asString(getClass.getResourceAsStream(path)))

  @volatile var response: ResponseContent = EmptyResponse

  lazy val client = new ConversationsClient(new EmptyClient {
      override def apply[A](r: Request[A]): CancellableFuture[Response] = CancellableFuture.successful(Response(HttpStatus(Success), response))
    }
  )
}
