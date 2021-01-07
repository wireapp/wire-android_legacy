package com.waz.model

import java.util.concurrent.TimeUnit

import com.waz.utils.JsonDecoder
import org.json.JSONObject

import scala.concurrent.duration.FiniteDuration

final case class AppLockFeatureFlag(enabled: Boolean, forced: Boolean, timeout: Option[FiniteDuration])

object AppLockFeatureFlag {
  val Default: AppLockFeatureFlag = AppLockFeatureFlag(enabled = false, forced = false, None)

  import JsonDecoder._

  final case class AppLockConfig(enforceAppLock: Boolean, inactivityTimeoutSecs: Int)

  implicit object Decoder extends JsonDecoder[AppLockFeatureFlag] {
    private def decodeConfig(implicit js: JSONObject): AppLockConfig = {
      AppLockConfig('enforceAppLock, 'inactivityTimeoutSecs)
    }

    override def apply(implicit js: JSONObject): AppLockFeatureFlag =
      if (js.getString("status") == "enabled") {
        val config = decodeConfig(js.getJSONObject("config"))
        AppLockFeatureFlag(
          enabled = true,
          forced  = config.enforceAppLock,
          timeout = Some(FiniteDuration(config.inactivityTimeoutSecs, TimeUnit.SECONDS))
        )
      } else
        Default
  }
}
