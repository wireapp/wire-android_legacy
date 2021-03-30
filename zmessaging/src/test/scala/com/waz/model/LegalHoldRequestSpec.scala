package com.waz.model

import com.waz.model.LegalHoldRequest.{Client, Prekey}
import com.waz.specs.AndroidFreeSpec
import com.waz.utils.{JsonDecoder, JsonEncoder}

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
          |    "key": "abc"
          |  }
          |}
          |""".stripMargin.replaceAll("\\s", "")

      // When
      val legalHoldRequest: LegalHoldRequest = JsonDecoder.decode[LegalHoldRequest](json)

      // Then
      legalHoldRequest.client.id shouldEqual "123"
      legalHoldRequest.lastPrekey.id shouldEqual 456
      legalHoldRequest.lastPrekey.key shouldEqual "abc"
    }

    scenario("to JSON") {
      // Given
      val legalHoldRequest = LegalHoldRequest(Client("123"), Prekey(456, "abc"))

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
          |    "key": "abc"
          |  }
          |}
          |""".stripMargin.replaceAll("\\s", "")
    }
  }
}
