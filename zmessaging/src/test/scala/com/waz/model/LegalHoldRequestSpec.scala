package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder

class LegalHoldRequestSpec extends AndroidFreeSpec {

  feature("Deserialization") {

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
          |""".stripMargin

      // When
      val legalHoldRequest: LegalHoldRequest = JsonDecoder.decode[LegalHoldRequest](json)

      // Then
      legalHoldRequest.client.id shouldEqual "123"
      legalHoldRequest.lastPrekey.id shouldEqual 456
      legalHoldRequest.lastPrekey.key shouldEqual "abc"
    }
  }
}
