package com.waz.model

import com.waz.model.LegalHoldRequest._
import com.waz.model.otr.ClientId
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.wire.cryptobox.PreKey

class LegalHoldRequestSpec extends AndroidFreeSpec {

  feature("Serialization") {

    scenario("from JSON") {
      // Given
      val json =
        """
          |{
          |  "client": {
          |    "id": "123"
          |  },
          |  "last_prekey": {
          |    "id": 456,
          |    "key": "oENwaFy74nagzFBlqn9nOQ=="
          |  }
          |}
          |""".stripMargin.replaceAll("\\s", "")

      // When
      val legalHoldRequest: LegalHoldRequest = JsonDecoder.decode[LegalHoldRequest](json)

      // Then
      legalHoldRequest.clientId.str shouldEqual "123"
      legalHoldRequest.lastPreKey.id shouldEqual 456
      legalHoldRequest.lastPreKey.data shouldEqual AESUtils.base64("oENwaFy74nagzFBlqn9nOQ==")
    }

    scenario("to JSON") {
      // Given
      val legalHoldRequest = LegalHoldRequest(
        ClientId("123"),
        new PreKey(456, AESUtils.base64("oENwaFy74nagzFBlqn9nOQ=="))
      )

      // When
      val json = JsonEncoder.encode[LegalHoldRequest](legalHoldRequest)

      // Then
      json.toString shouldEqual
        """
          |{
          |  "client": {
          |    "id": "123"
          |  },
          |  "last_prekey": {
          |    "id": 456,
          |    "key": "oENwaFy74nagzFBlqn9nOQ=="
          |  }
          |}
          |""".stripMargin.replaceAll("\\s", "")
    }
  }
}
