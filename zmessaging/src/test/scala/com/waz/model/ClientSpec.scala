package com.waz.model

import com.waz.api.Verification
import com.waz.model.otr.Client.{DeviceClass, DeviceType}
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.OtrClient.ListClientsRequest
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.sync.client.OtrClient.ListClientsResponse._
import org.threeten.bp.Instant

class ClientSpec extends AndroidFreeSpec {
  private val domain = "domain1.example.com"
  private val userId = UserId("000600d0-000b-9c1a-000d-a4130002c221")
  private val client = Client(id = ClientId("d0"), label = "phone")

  private val qualifiedId = QualifiedId(userId, domain)

  feature("Client list (de)serialization") {

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

    scenario("Serialize a client list request to JSON") {
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

  feature("UserClients (de)serialization") {

    val client = Client(
      id = ClientId("d0"),
      label = "phone",
      model = "Pixel 4",
      verified = Verification.VERIFIED,
      deviceClass = DeviceClass.Tablet,
      deviceType = Some(DeviceType.Temporary),
      regTime = Some(Instant.now()),
      isTemporary = true
    )

    scenario("Deserialize client from JSON") {
      // given
      val json =
        s"""
           |{
           |  "user": "${userId.str}",
           |  "clients": [
           |    {
           |      "id": "${client.id.str}",
           |      "label": "${client.label}",
           |      "model": "${client.model}",
           |      "verification": "${client.verified.name}",
           |      "class": "${client.deviceClass.value}",
           |      "type": "${client.deviceType.get.value}",
           |      "regTime": ${client.regTime.get.toEpochMilli},
           |      "isTemporary": ${client.isTemporary}
           |    }
           |  ]
           |}
           |""".stripMargin

      // when
      val result = JsonDecoder.decode(json)(UserClients.Decoder)

      // then
      result.id shouldEqual userId
      result.clients.size shouldEqual  1

      val (decodedClientId, decodedClient) = result.clients.head
      decodedClientId shouldEqual client.id

      decodedClient.id shouldEqual client.id
      decodedClient.label shouldEqual client.label
      decodedClient.model shouldEqual client.model
      decodedClient.verified shouldEqual client.verified
      decodedClient.deviceClass shouldEqual client.deviceClass
      decodedClient.deviceType shouldEqual client.deviceType
      decodedClient.regTime shouldEqual client.regTime
      decodedClient.isTemporary shouldEqual true
    }

    scenario("Deserialize client from legacy JSON") {
      // given
      val json =
        s"""
           |{
           |  "user": "${userId.str}",
           |  "clients": [
           |    {
           |      "id": "${client.id.str}",
           |      "label": "${client.label}",
           |      "model": "${client.model}",
           |      "verification": "${client.verified.name}",
           |      "devType": "${client.deviceClass.value}",
           |      "regTime": ${client.regTime.get.toEpochMilli}
           |    }
           |  ]
           |}
           |""".stripMargin

      // when
      val result = JsonDecoder.decode(json)(UserClients.Decoder)

      // then
      result.id shouldEqual userId
      result.clients.size shouldEqual  1

      val (decodedClientId, decodedClient) = result.clients.head
      decodedClientId shouldEqual client.id

      decodedClient.id shouldEqual client.id
      decodedClient.label shouldEqual client.label
      decodedClient.model shouldEqual client.model
      decodedClient.verified shouldEqual client.verified
      decodedClient.deviceClass shouldEqual client.deviceClass
      decodedClient.deviceType shouldEqual None
      decodedClient.regTime shouldEqual client.regTime
      decodedClient.isTemporary shouldEqual false
    }

    scenario("Serialize client to JSON") {
      // given
      val userClients = UserClients(userId, Map(client.id -> client))

      // when
      val result = JsonEncoder.encode(userClients)(UserClients.Encoder)

      // then
      val json =
        s"""|{
            |  "user": "${userId.str}",
            |  "clients": [
            |    {
            |      "id": "${client.id.str}",
            |      "label": "${client.label}",
            |      "model": "${client.model}",
            |      "verification": "${client.verified.name}",
            |      "class": "${client.deviceClass.value}",
            |      "type": "${client.deviceType.get.value}",
            |      "regTime": ${client.regTime.get.toEpochMilli},
            |      "isTemporary": ${client.isTemporary}
            |    }
            |  ]
            |}""".stripMargin

      result.toString(2) shouldEqual json
    }
  }
}
