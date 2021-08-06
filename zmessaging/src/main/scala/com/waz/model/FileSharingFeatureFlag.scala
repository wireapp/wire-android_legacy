package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class FileSharingFeatureFlag(status: String) {

  def isEnabled: Boolean = status == "enabled"

}

object FileSharingFeatureFlag {

  val Default: FileSharingFeatureFlag = FileSharingFeatureFlag(status = "enabled")

  implicit object Decoder extends JsonDecoder[FileSharingFeatureFlag] {
    override def apply(implicit js: JSONObject): FileSharingFeatureFlag =
      if (!js.has("status")) Default
      else FileSharingFeatureFlag(js.getString("status"))
  }

}
