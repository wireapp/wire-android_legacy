package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder
import scala.concurrent.duration._

class AppLockFeatureConfigSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled config") {
      val json =
        """
          |{
          |  "status": "enabled",
          |  "config": {
          |    "enforceAppLock": true,
          |    "inactivityTimeoutSecs": 60
          |  }
          |}
          |""".stripMargin

      val appLockFeatureConfig: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureConfig.enabled shouldEqual true
      appLockFeatureConfig.forced shouldEqual true
      appLockFeatureConfig.timeout shouldEqual Some(60.seconds)
    }

    scenario("Deserializing a disabled config") {
      val json =
        """
          |{
          |  "status": "disabled"
          |}
          |""".stripMargin

      val appLockFeatureConfig: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureConfig shouldEqual AppLockFeatureConfig.Disabled
    }

    scenario("Deserializing an error (empty json object)") {
      val json = "{}"
      val appLockFeatureConfig: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureConfig shouldEqual AppLockFeatureConfig.Default
    }
  }
}
