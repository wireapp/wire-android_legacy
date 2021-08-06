package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class FileSharingFeatureFlag(enabled: Boolean)

object FileSharingFeatureFlag {

  val Default: FileSharingFeatureFlag = FileSharingFeatureFlag(enabled = true)

  implicit object Decoder extends JsonDecoder[FileSharingFeatureFlag] {
    override def apply(implicit js: JSONObject): FileSharingFeatureFlag =
      if (!js.has("status")) Default
      else FileSharingFeatureFlag(enabled = js.getString("status") == "enabled")
  }

}
