package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class ConferenceCallingFeatureConfig(status: String) {
  def isEnabled: Boolean = status == "enabled"
}

object ConferenceCallingFeatureConfig {

  val Default: ConferenceCallingFeatureConfig = ConferenceCallingFeatureConfig(status = "enabled")

  implicit object Decoder extends JsonDecoder[ConferenceCallingFeatureConfig] {
    override def apply(implicit js: JSONObject): ConferenceCallingFeatureConfig =
      if (!js.has("status")) Default
      else ConferenceCallingFeatureConfig(js.getString("status"))
  }

}
