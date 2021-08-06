package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder

class FileSharingFeatureFlagSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled flag") {
      // Given
      val json =
        """
          |{
          |  "status": "enabled"
          |}
          |""".stripMargin

      // When
      val fileSharingFeatureFlag: FileSharingFeatureFlag = JsonDecoder.decode[FileSharingFeatureFlag](json)

      // Then
      fileSharingFeatureFlag.enabled shouldEqual true
    }

    scenario("Deserializing a disabled flag") {
      // Given
      val json =
        """
          |{
          |  "status": "disabled"
          |}
          |""".stripMargin

      // When
      val fileSharingFeatureFlag: FileSharingFeatureFlag = JsonDecoder.decode[FileSharingFeatureFlag](json)

      // Then
      fileSharingFeatureFlag.enabled shouldEqual false
    }

    scenario("Deserializing an error (empty json object)") {
      // Given
      val json = "{}"

      // When
      val fileSharingFeatureFlag: FileSharingFeatureFlag = JsonDecoder.decode[FileSharingFeatureFlag](json)

      // Then
      fileSharingFeatureFlag shouldEqual FileSharingFeatureFlag.Default
    }
  }

}
