package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class SelfDeletingMessagesFeatureConfig(status: String, seconds: Int) {

  def isEnabled: Boolean = status == "enabled"

  def enforcedTimeoutInSeconds: Int = Math.max(0, seconds)

}

object SelfDeletingMessagesFeatureConfig {

  val Default: SelfDeletingMessagesFeatureConfig = SelfDeletingMessagesFeatureConfig(status = "enabled", 0)

  implicit object Decoder extends JsonDecoder[SelfDeletingMessagesFeatureConfig] {
    override def apply(implicit js: JSONObject): SelfDeletingMessagesFeatureConfig =
      if (!js.has("status") || !js.has("enforcedTimeoutSeconds")) Default
      else SelfDeletingMessagesFeatureConfig(js.getString("status"), js.getInt("enforcedTimeoutSeconds"))
  }

}
