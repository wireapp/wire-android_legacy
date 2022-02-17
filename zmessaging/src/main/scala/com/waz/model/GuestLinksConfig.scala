package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class GuestLinksConfig(status: String) {
  def isEnabled: Boolean = status == "enabled"
}

object GuestLinksConfig {
  val Default: GuestLinksConfig = GuestLinksConfig(status = "enabled")

  implicit object Decoder extends JsonDecoder[GuestLinksConfig] {
    override def apply(implicit js: JSONObject): GuestLinksConfig =
      if (!js.has("status")) Default
      else GuestLinksConfig(js.getString("status"))
  }
}
