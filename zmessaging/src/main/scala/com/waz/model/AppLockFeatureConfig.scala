package com.waz.model

import java.util.concurrent.TimeUnit

import com.waz.utils.JsonDecoder
import org.json.JSONObject

import scala.concurrent.duration.FiniteDuration

final case class AppLockFeatureConfig(enabled: Boolean, forced: Boolean, timeout: Option[FiniteDuration])

object AppLockFeatureConfig {
  val Default: AppLockFeatureConfig  = AppLockFeatureConfig(enabled = true, forced = false, None)
  val Disabled: AppLockFeatureConfig = AppLockFeatureConfig(enabled = false, forced = false, None)

  import JsonDecoder._

  final case class AppLockConfig(enforceAppLock: Boolean, inactivityTimeoutSecs: Int)

  implicit object Decoder extends JsonDecoder[AppLockFeatureConfig] {
    private def decodeConfig(implicit js: JSONObject): AppLockConfig = {
      AppLockConfig('enforceAppLock, 'inactivityTimeoutSecs)
    }

    override def apply(implicit js: JSONObject): AppLockFeatureConfig =
      if (!js.has("status")) {
        Default
      } else if (js.getString("status") == "enabled") {
        val config = decodeConfig(js.getJSONObject("config"))
        AppLockFeatureConfig(
          enabled = true,
          forced  = config.enforceAppLock,
          timeout = Some(FiniteDuration(config.inactivityTimeoutSecs, TimeUnit.SECONDS))
        )
      } else {
        Disabled
      }
  }
}
