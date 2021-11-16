package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class FileSharingFeatureConfig(status: String) {

  def isEnabled: Boolean = status == "enabled"

}

object FileSharingFeatureConfig {

  val Default: FileSharingFeatureConfig = FileSharingFeatureConfig(status = "enabled")

  implicit object Decoder extends JsonDecoder[FileSharingFeatureConfig] {
    override def apply(implicit js: JSONObject): FileSharingFeatureConfig =
      if (!js.has("status")) Default
      else FileSharingFeatureConfig(js.getString("status"))
  }
}
