package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder
import scala.concurrent.duration._

class AppLockFeatureConfigSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled flag") {
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

      val appLockFeatureFlag: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureFlag.enabled shouldEqual true
      appLockFeatureFlag.forced shouldEqual true
      appLockFeatureFlag.timeout shouldEqual Some(60.seconds)
    }

    scenario("Deserializing a disabled flag") {
      val json =
        """
          |{
          |  "status": "disabled"
          |}
          |""".stripMargin

      val appLockFeatureFlag: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureFlag shouldEqual AppLockFeatureConfig.Disabled
    }

    scenario("Deserializing an error (empty json object)") {
      val json = "{}"
      val appLockFeatureFlag: AppLockFeatureConfig = JsonDecoder.decode[AppLockFeatureConfig](json)

      appLockFeatureFlag shouldEqual AppLockFeatureConfig.Default
    }
  }
}
