package com.waz.model

import com.waz.model.otr.{Client, ClientId}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.OtrClient.ListClientsRequest
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.sync.client.OtrClient.ListClientsResponse._

class ClientSpec extends AndroidFreeSpec {
  private val domain = "domain1.example.com"
  private val userId = UserId("000600d0-000b-9c1a-000d-a4130002c221")
  private val client = Client(id = ClientId("d0"), label = "phone")

  private val qualifiedId = QualifiedId(userId, domain)

  scenario("Deserialize a client list response from JSON") {
    // given
    val json =
    s"""
       |{
       |  "qualified_user_map": {
       |    "$domain": {
       |      "${userId.str}": [
       |        {
       |          "id": "${client.id.str}",
       |          "label": "${client.label}"
       |        }
       |      ]
       |    }
       |  }
       |}
       |""".stripMargin

    // when
    val response = JsonDecoder.decode(json)

    // then
    response.values.size shouldEqual 1
    response.values.keys.head shouldEqual qualifiedId
    response.values(qualifiedId).size shouldEqual 1
    response.values(qualifiedId).head shouldEqual client
  }

  scenario("Serialize a client list request toJSON") {
    // given
    val req = ListClientsRequest(Seq(qualifiedId))

    // when
    val response = JsonEncoder.encode(req)

    // then
    response.has("qualified_users") shouldEqual true
    val array = response.getJSONArray("qualified_users")
    array.length() shouldEqual 1
    val qUser = array.getJSONObject(0)
    val qId = QualifiedId.Decoder(qUser)
    qId shouldEqual qualifiedId
  }
}
