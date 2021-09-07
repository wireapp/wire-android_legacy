package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder

class ConferenceCallingFeatureConfigSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled config") {
      // Given
      val json =
        """
          |{
          |  "status": "enabled"
          |}
          |""".stripMargin

      // When
      val conferenceCallingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      conferenceCallingFeatureConfig.isEnabled shouldEqual true
    }

    scenario("Deserializing a disabled config") {
      // Given
      val json =
        """
          |{
          |  "status": "disabled"
          |}
          |""".stripMargin

      // When
      val conferenceCallingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      conferenceCallingFeatureConfig.isEnabled shouldEqual false
    }

    scenario("Deserializing an error (empty json object)") {
      // Given
      val json = "{}"

      // When
      val conferenceCallingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      conferenceCallingFeatureConfig shouldEqual ConferenceCallingFeatureConfig.Default
    }
  }
}
