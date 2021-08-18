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
      val fileSharingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      fileSharingFeatureConfig.isEnabled shouldEqual true
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
      val fileSharingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      fileSharingFeatureConfig.isEnabled shouldEqual false
    }

    scenario("Deserializing an error (empty json object)") {
      // Given
      val json = "{}"

      // When
      val fileSharingFeatureConfig: ConferenceCallingFeatureConfig = JsonDecoder.decode[ConferenceCallingFeatureConfig](json)

      // Then
      fileSharingFeatureConfig shouldEqual ConferenceCallingFeatureConfig.Default
    }
  }
}
