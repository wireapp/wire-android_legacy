package com.waz.model

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonDecoder
import scala.concurrent.duration._

class AppLockFeatureFlagSpec extends AndroidFreeSpec {

  feature("Deserialization form JSON") {

    scenario("Deserializing an enabled") {
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

      val appLockFeatureFlag: AppLockFeatureFlag = JsonDecoder.decode[AppLockFeatureFlag](json)

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

      val appLockFeatureFlag: AppLockFeatureFlag = JsonDecoder.decode[AppLockFeatureFlag](json)

      appLockFeatureFlag shouldEqual AppLockFeatureFlag.Default
    }
  }
}
