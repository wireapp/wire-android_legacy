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

import java.util.Date

import com.waz.model.Event.EventDecoder
import com.waz.model.nano.Messages
import com.waz.model.otr.ClientId
import com.waz.utils.JsonDecoder
import org.json.JSONObject
import org.scalatest._
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.threeten.bp.Instant

class EventSpec extends FeatureSpec with Matchers with BeforeAndAfter with GivenWhenThen with PropertyChecks with GeneratorDrivenPropertyChecks with RobolectricTests {
  import EventSpec._
  import MessageEvent._

  feature("Event parsing") {
    scenario("parse UserConnectionEvent") {

      Given("some json data")
      val js = new JSONObject(userConectionEventData)

      When("parsing json")
      val event = EventDecoder(js)

      Then("we should have a UserConnectionEvent")
      event.isInstanceOf[UserConnectionEvent] shouldEqual true
      event.asInstanceOf[UserConnectionEvent].status should be(UserData.ConnectionStatus.PendingFromUser)
      event.asInstanceOf[UserConnectionEvent].convId should be(RConvId("f660330f-f0e3-4511-8d15-71251f44ce32"))
      event.asInstanceOf[UserConnectionEvent].to should be(otherUser.id)
      event.asInstanceOf[UserConnectionEvent].from should be(selfUser.id)
      event.asInstanceOf[UserConnectionEvent].lastUpdated should be(JsonDecoder.parseDate("2014-06-12T10:04:02.047Z"))
      event.asInstanceOf[UserConnectionEvent].message should be(Some("Hello Test"))
    }

    scenario("parse otr message event") {
      EventDecoder(new JSONObject(OtrMessageEvent)) match {
        case ev: OtrMessageEvent =>
          ev.convId shouldEqual RConvId("dd2d342a-0756-4710-a033-0544d2752570")
          ev.sender shouldEqual ClientId("44184d922af83522")
          ev.recipient shouldEqual ClientId("b4ca17e659751527")
        case e => fail(s"unexpected event: $e")
      }
    }

    scenario("encode/decode GenericMessageEvent") {
      val msg = GenericMessageEvent(RConvId(), Instant.now(), UserId(), new Messages.GenericMessage)
      EventDecoder(MessageEventEncoder(msg)) match {
        case ev: GenericMessageEvent =>
          ev.convId shouldEqual msg.convId
          ev.content.equals(msg.content)
          ev.from shouldEqual msg.from
          ev.time shouldEqual msg.time
        case e => fail(s"unexpected event: $e")
      }
    }

    scenario("encode/decode CallMessageEvent") {
      val msg = CallMessageEvent(RConvId(), Instant.now(), UserId(), ClientId(), "")
      EventDecoder(MessageEventEncoder(msg)) match {
        case ev: CallMessageEvent =>
          ev.convId shouldEqual msg.convId
          ev.time shouldEqual msg.time
          ev.from shouldEqual msg.from
          ev.sender shouldEqual msg.sender
          ev.content shouldEqual msg.content
        case e => fail(s"unexpected event: $e")
      }
    }

    scenario("encode/decode OtrErrorEvent(duplicate)") {
      val msg = OtrErrorEvent(RConvId(), Instant.now(), UserId(), Duplicate)
      EventDecoder(MessageEventEncoder(msg)) match {
        case ev: OtrErrorEvent =>
          ev.convId shouldEqual msg.convId
          ev.time shouldEqual msg.time
          ev.from shouldEqual msg.from
          ev.error shouldEqual msg.error
        case e => fail(s"unexpected event: $e")
      }
    }

    scenario("encode/decode OtrErrorEvent(DecryptionError)") {
      val msg = OtrErrorEvent(RConvId(), Instant.now(), UserId(), DecryptionError("error", UserId(), ClientId()))
      EventDecoder(MessageEventEncoder(msg)) match {
        case ev: OtrErrorEvent =>
          ev.convId shouldEqual msg.convId
          ev.time shouldEqual msg.time
          ev.from shouldEqual msg.from
          ev.error shouldEqual msg.error
        case e => fail(s"unexpected event: $e")
      }
    }

    scenario("encode/decode OtrErrorEvent(IdentityChanged)") {
      val msg = OtrErrorEvent(RConvId(), Instant.now(), UserId(), IdentityChangedError(UserId(), ClientId()))
      EventDecoder(MessageEventEncoder(msg)) match {
        case ev: OtrErrorEvent =>
          ev.convId shouldEqual msg.convId
          ev.time shouldEqual msg.time
          ev.from shouldEqual msg.from
          ev.error shouldEqual msg.error
        case e => fail(s"unexpected event: $e")
      }
    }
  }
}

object EventSpec {
  val selfUser = UserData("Self User")
  val otherUser = UserData("Other User")

  val userConectionEventData =
    s"""{
       |  "connection": {
       |    "status": "sent",
       |    "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
       |    "to": "${otherUser.id}",
       |    "from": "${selfUser.id}",
       |    "last_update": "2014-06-12T10:04:02.047Z",
       |    "message": "Hello Test"
       |  },
       |  "type": "user.connection"
       |}""".stripMargin

  val PreviewImageEvent =
    """{
      |  "conversation": "a50acb79-d7a3-4fdc-b885-e779ac0c0689",
      |  "time": "2014-05-24T10:55:53.030Z",
      |  "data": {
      |    "content_length": 870,
      |    "data": "/9j/4AAQSkZJRgABAQAAAQABAAD/4QBYRXhpZgAATU0AKgAAAAgAAgESAAMAAAABAAYAAIdpAAQAAAABAAAAJgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAJqADAAQAAAABAAAAHQAAAAD/2wBDACAWGBwYFCAcGhwkIiAmMFA0MCwsMGJGSjpQdGZ6eHJmcG6AkLicgIiuim5woNqirr7EztDOfJri8uDI8LjKzsb/2wBDASIkJDAqMF40NF7GhHCExsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsbGxsb/wAARCAAdACYDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwCS3uwlsUeQElsjtVKSTcxCj86jLLjK5BxzUiIBjnnGTmi2pJZ08GQtE+CC3IPfiq+pqEuAEHyqoA+gqW3O2YnkAnJI61XuSrXEgJJ2ucHPvQUVWdSxLLk0VGf1opiLsSlV3P1PQUquM7jznio5nPQUxiVIHWgCdpyiMVwD0AqoG5Ynn3NPkYFeFxwTUJHFIY+Py2J3L+tFRiiqEf/Z",
      |    "content_type": "image/jpeg",
      |    "id": "bff4c7c2-6071-583b-80c5-97ea96e0a85f",
      |    "info": {
      |      "height": 29,
      |      "tag": "preview",
      |      "original_width": 1802,
      |      "width": 38,
      |      "correlation_id": "04533b8c-dab4-f461-6ab2-b602449d01b1",
      |      "original_height": 1352,
      |      "nonce": "3ca6ec2e-205b-83f5-f265-a13bcdd8ea1c",
      |      "public": false
      |    }
      |  },
      |  "from": "455aa02f-2758-4459-ab15-768ed4cad936",
      |  "id": "3.800122000a272fd6",
      |  "type": "conversation.asset-add"
      |}""".stripMargin

  val MediumImageEvent =
    """{
      |  "conversation": "a50acb79-d7a3-4fdc-b885-e779ac0c0689",
      |  "time": "2014-05-24T10:55:59.069Z",
      |  "data": {
      |    "content_length": 334927,
      |    "data": null,
      |    "content_type": "image/jpeg",
      |    "id": "da498123-6cda-5c3a-a0bd-396972801e91",
      |    "info": {
      |      "height": 1352,
      |      "tag": "medium",
      |      "original_width": 3264,
      |      "width": 1802,
      |      "correlation_id": "04533b8c-dab4-f461-6ab2-b602449d01b1",
      |      "original_height": 2448,
      |      "nonce": "b68a0db3-593a-1a1d-aa46-1d2ec228c474",
      |      "public": false
      |    }
      |  },
      |  "from": "455aa02f-2758-4459-ab15-768ed4cad936",
      |  "id": "4.800122000a272fd7",
      |  "type": "conversation.asset-add"
      |}""".stripMargin

  val ImaginaryAudioEvent =
    """{
      |  "conversation": "a50acb79-d7a3-4fdc-b885-e779ac0c0689",
      |  "time": "2014-05-24T10:55:59.069Z",
      |  "data": {
      |    "content_length": 1048576,
      |    "data": null,
      |    "content_type": "audio/aac",
      |    "id": "1fb6fec2-7c82-4921-85f1-6245bc795ab4",
      |    "info": {
      |      "duration": "PT42S",
      |      "bit_rate": "192",
      |      "sampling_rate": "44100"
      |    }
      |  },
      |  "from": "455aa02f-2758-4459-ab15-768ed4cad936",
      |  "id": "5.800122000a272fd7",
      |  "type": "conversation.asset-add"
      |}""".stripMargin

  val OtrMessageEvent =
    """{
      |  "time":"2015-07-03T15:25:13.527558000000Z",
      |  "data":{
      |     "sender":"44184d922af83522",
      |     "text":"AAAAAQAAAAAAAAAgha0KAUlbvJuueoigdS1cDhZH4AJVp4xmDv7zb2b6BKcAAAAAAAAAzgAAAAIAAQAAAAAAAAAgeqidtPXlu1D5UfhSKlktO9ZQOYe3YQKzywol55dn4KsAAAAAAAAAIDv4DJYmBYe1VHs7ifnMNicAiJrRt3ekeaYBdkVIp5SUAAAAAAAAABC0x6rwKHIsdexmkY\/SUf7dAAAAAAAAAAAAAAAAAAAAIJ7muZOG5JvpmIyitHNsEuhf0GWyHzR+dL1wmXfF8\/cZAAAAAAAAACiwRzc0GxT05zRRrvSdH4LApgzRXzv7eqgvroY+4kFLkTHVuHaDj494",
      |     "recipient":"b4ca17e659751527"
      |  },
      |  "conversation":"dd2d342a-0756-4710-a033-0544d2752570",
      |  "from":"e9e837d6-a12c-492f-9938-7fe61af3971c",
      |  "type":"conversation.otr-message-add"
      |}""".stripMargin

  val OtrAssetEvent =
    """{
      |   "time": "2015-08-19T15:32:28.975Z",
      |   "data": {
      |      "id": "fb325cac-d2d8-4afe-b236-35ac438a9e83",
      |      "sender": "b79a049114ff051e",
      |      "recipient": "650d1a5efc422126",
      |      "key": "gwFYIEqZoz5mJuE/ED5Lu8xKghry2J47ioSImmBN25J2VBm7WJUBUA3t7pjIWElaMd8i/oEKXFEBAVggjYIL1I2gyqBvBGjwK6zOqRTi9KdZ4tPvgQxGon9bdO1YXRgPNx5krQRF5CyJWRukXlZFFA2mdxbf/sUWVUlbcaTOymlNuEaDxFN7K5paQw+9desV39fNx1yDgRLfLLWK9Cet936lZVS2nYVHmncuHRu2t2knlJVkpHe90TCE5A=="
      |   },
      |   "conversation": "74f85659-4677-4b17-91dd-d49cd703b234",
      |   "from": "bf59ae41-3dca-4099-b871-ce5940939166",
      |   "type": "conversation.otr-asset-add"
      |}""".stripMargin

  val OtrAssetEvent1 =
    """{
      |   "time":"2015-08-20T09:35:07.552Z",
      |   "data":{
      |      "id":"2e8c9b0e-9a98-41aa-bf0e-f1395a3b5b39",
      |      "sender":"80b8a91aeb4b4dd",
      |      "data":"z4kzPCP7Lc3MQzap5uqMPdA4UyYsptTem9Rfvalo1WENnq9NScp+EChXRb+bKH9D7oXDXofkTSxwSRmFbzMsOp\/Wo3bGV\/8nUtfXAznRUA6u8L+7IZnqpCk82UkEHPFkI1Hv5AuOgv9UCan1JhS3qL1GKOznUNcJAZUkwBYuhDouM+dGtjyAiY1xcMDEkrHne4gInJP3Ehg+hNx\/HZO6w15L6r4mC2BYy8g+NMdJQAsKdP\/Ye1fxB7hXVoHWZkiI54gStqFAxsUGqJIiznAm3Za6TNjyT\/pLl8d4d5t7c1aE7gDEt8IMQAL3OHlwxv9OkLfs2dPZAjdFZpqJh4INCrqdPBojKxSUwWm9O9A\/9jAt0CRUXeivwy22YmXrgVB0trkgfWBS5Q+QlxQH7ipZ3oGToCl5GdBwnqhq8cOPLdxe9tqdI66UMrEGDvS0bozjo3vUeB8lGUcVCH6HZq\/98FdTSYE5WE57OUJx1yolp75C4\/KijdfHgmZYB9vzDDJoF\/G\/LspKhxbgijnQcB+HpVrk3HTFoYSC7K5231au1dzbTZYb5VPfy1bhAoZCBFoDfwVlFNwdacauhVIJGvGpsyBMBRBMC0aV3kDt7XbyRnRdPW6PT91ML5i0FB051x7yyNvzz6z0qON7M3LcE9OpLrjJVu8Ex30514DQIj7lpCajZlrr5B1t7qhZeqGgwzb5v\/LGxdapzyrTRii0c4U1rZeiYxq70KKxdEJrpRHMYAXexYaOWV48OmCrk4sELmNg5q6gnjZKzDhMpmNGWU5+bLfvgQP1hDQ7g+z29wbPU25F8geD016Yml42o9B5f3WbEjQNib8bpW7zfMR8BtAU04\/zYMAJHV52fc0y3FVY1gE8OqGOYIk8gQ0xu6FT3I5g4nJrTWYRX+JHYjxyb54c5H1m\/bPV9p9+9\/z3iuKaWibop2GbMBI753f6Yl4qm8SRSCFtLOfbhxMOMouiCybtUJEaLS483Ut72ThAM4Yh8pSID9EgGK9bWB2NpweEEDEufe\/vYq91yxnsNNvYdSNo0Q==",
      |      "recipient":"ff23d4857147e00c",
      |      "key":"gwFYIISpSrQ\/SyUBOztOhzqkUtdx\/Hos\/s6PI0RVS6k9bfX+WKQBUDrBCBeeD1\/haWMm\/6Z+LGkBAFggWruzBEiPmq7Slu9uL47OwK6vwfXsoOCKVEXKWHy9Q6hYbF8bO3GM\/B6AGGsNc8qQLWfq8eRyx83FdHL618supmkoKAzav66F9afNgg266080JDP6uWZZlPEgsi9jZOMvpV428mLts\/G7+rhTbBkiNNTbG6x+d0oinSKRl4UyGY2YrN9Locbkz\/9tYPTWjQ=="
      |   },
      |   "conversation":"e74e62ea-1bcd-4582-ab12-bf7a0ff43931",
      |   "from":"dbf13c1b-b7f5-49fd-988b-9eed329d43a8",
      |   "type":"conversation.otr-asset-add"
      |}""".stripMargin
}
