/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.call

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.otr.ClientId
import com.waz.model.{ConvId, UserId}
import com.waz.service.call.Avs.{AvsClient, AvsClientList}
import com.waz.service.call.Avs.AvsClientList._
import com.waz.specs.AndroidFreeSpec

class AvsSpec extends AndroidFreeSpec with DerivedLogTag  {

  scenario("Group participants change Json can be decoded") {
    // Given
    val json =
      """
        |{
        |  "convid": "df371578-65cf-4f07-9f49-c72a49877ae7",
        |  "members": [
        |    {
        |      "userid": "3f49da1d-0d52-4696-9ef3-0dd181383e8a",
        |      "clientid": "24cc758f602fb1f4",
        |      "aestab": 1,
        |      "vrecv": 0,
        |      "muted": 1
        |    },
        |    {
        |      "userid": "7cc36a2e-88d3-ac76-86ba-d02faca478ed",
        |      "clientid": "64ca916f366fcc56",
        |      "aestab": 0,
        |      "vrecv": 1,
        |      "muted": 0
        |    }
        |  ]
        |}
      """.stripMargin

    // When
    val result = Avs.ParticipantsChangeDecoder.decode(json)

    // Then
    result.isDefined shouldEqual true
    result.get.convid shouldEqual ConvId("df371578-65cf-4f07-9f49-c72a49877ae7")
    result.get.members.size shouldEqual 2

    val member1 = result.get.members.head
    member1.userid shouldEqual UserId("3f49da1d-0d52-4696-9ef3-0dd181383e8a")
    member1.clientid shouldEqual ClientId("24cc758f602fb1f4")
    member1.aestab shouldEqual 1
    member1.vrecv shouldEqual 0
    member1.muted shouldEqual 1

    val member2 = result.get.members.last
    member2.userid shouldEqual UserId("7cc36a2e-88d3-ac76-86ba-d02faca478ed")
    member2.clientid shouldEqual ClientId("64ca916f366fcc56")
    member2.aestab shouldEqual 0
    member2.vrecv shouldEqual 1
    member2.muted shouldEqual 0
  }

  scenario("Client list can be encoded") {
    // Given
    val clientList = AvsClientList(Seq(
      AvsClient("user1", "client1"),
      AvsClient("user1", "client2"),
      AvsClient("user2", "client1"),
      AvsClient("user3", "client1"),
      AvsClient("user3", "client2")
    ))

    // When
    val result = encode(clientList)

    // Then
    minify(result) shouldEqual minify(
      """
        |{
        |  "clients" : [
        |    {
        |      "userid" : "user1",
        |      "clientid" : "client1"
        |    },
        |    {
        |      "userid" : "user1",
        |      "clientid" : "client2"
        |    },
        |    {
        |      "userid" : "user2",
        |      "clientid" : "client1"
        |    },
        |    {
        |      "userid" : "user3",
        |      "clientid" : "client1"
        |    },
        |    {
        |      "userid" : "user3",
        |      "clientid" : "client2"
        |    }
        |  ]
        |}""".stripMargin)
  }

  private def minify(text: String): String = text.replaceAll("\\s", "")

}
