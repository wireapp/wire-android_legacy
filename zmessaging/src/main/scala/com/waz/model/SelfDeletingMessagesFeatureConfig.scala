package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class SelfDeletingMessagesFeatureConfig(isEnabled: Boolean, private val seconds: Int) {

  def enforcedTimeoutInSeconds: Int = Math.max(0, seconds)

}

object SelfDeletingMessagesFeatureConfig {

  val Default: SelfDeletingMessagesFeatureConfig = SelfDeletingMessagesFeatureConfig(isEnabled = true, 0)

  implicit object Decoder extends JsonDecoder[SelfDeletingMessagesFeatureConfig] {
    override def apply(implicit js: JSONObject): SelfDeletingMessagesFeatureConfig =
      if (!js.has("status")) Default
      else if (js.getString("status") != "enabled") SelfDeletingMessagesFeatureConfig(isEnabled = false, 0)
      else {
        val config = js.optJSONObject("config")
        val enforcedTimeoutSeconds = if (config != null && config.has("enforcedTimeoutSeconds"))
          config.getInt("enforcedTimeoutSeconds")
        else Default.enforcedTimeoutInSeconds
        SelfDeletingMessagesFeatureConfig(isEnabled = true, enforcedTimeoutSeconds)
      }
  }
}
