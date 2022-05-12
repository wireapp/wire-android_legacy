package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder

class SelfDeletingMessagesFeatureConfigSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled config with enforced timeout") {
      // Given
      val json =
        """
          |{
          |  "status": "enabled",
          |  "config":{
          |    "enforcedTimeoutSeconds": 30
          |  }
          |}
          |""".stripMargin

      // When
      val selfDeletingMessagesFeatureConfig: SelfDeletingMessagesFeatureConfig = JsonDecoder.decode[SelfDeletingMessagesFeatureConfig](json)

      // Then
      selfDeletingMessagesFeatureConfig.isEnabled shouldEqual true
      selfDeletingMessagesFeatureConfig.enforcedTimeoutInSeconds shouldEqual 30
    }
    scenario("Deserializing an enabled config without enforced timeout") {
      // Given
      val json =
        """
          |{
          |  "status": "enabled",
          |  "config":{
          |    "enforcedTimeoutSeconds": 0
          |  }
          |}
          |""".stripMargin

      // When
      val selfDeletingMessagesFeatureConfig: SelfDeletingMessagesFeatureConfig = JsonDecoder.decode[SelfDeletingMessagesFeatureConfig](json)

      // Then
      selfDeletingMessagesFeatureConfig.isEnabled shouldEqual true
      selfDeletingMessagesFeatureConfig.enforcedTimeoutInSeconds shouldEqual 0
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
      val selfDeletingMessagesFeatureConfig: SelfDeletingMessagesFeatureConfig = JsonDecoder.decode[SelfDeletingMessagesFeatureConfig](json)

      // Then
      selfDeletingMessagesFeatureConfig.isEnabled shouldEqual false
      selfDeletingMessagesFeatureConfig.enforcedTimeoutInSeconds shouldEqual 0
    }

    scenario("Deserializing an error (empty json object)") {
      // Given
      val json = "{}"

      // When
      val selfDeletingMessagesFeatureConfig: SelfDeletingMessagesFeatureConfig = JsonDecoder.decode[SelfDeletingMessagesFeatureConfig](json)

      // Then
      selfDeletingMessagesFeatureConfig shouldEqual SelfDeletingMessagesFeatureConfig.Default
    }
  }

}
