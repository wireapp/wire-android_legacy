package com.waz.model

import com.waz.utils.JsonDecoder
import org.json.JSONObject

final case class ClassifiedDomainsConfig(isEnabled: Boolean, domains: Set[Domain]) {
  def serialize: Option[String] =
    if (isEnabled)
      Some(domains.map(_.str).mkString(","))
    else
      None
}

object ClassifiedDomainsConfig {
  val Disabled: ClassifiedDomainsConfig = ClassifiedDomainsConfig(isEnabled = false, Set.empty)

  def deserialize(str: Option[String]): ClassifiedDomainsConfig =
    str match {
      case None =>
        Disabled
      case Some(domainsStr) =>
        val domains = domainsStr.split(",").map(d => Domain(Some(d))).toSet
        ClassifiedDomainsConfig(isEnabled = true, domains)
    }

  import JsonDecoder._

  implicit object Decoder extends JsonDecoder[ClassifiedDomainsConfig] {
    override def apply(implicit js: JSONObject): ClassifiedDomainsConfig =
      if (!js.has("status") || js.getString("status") != "enabled")
        Disabled
      else {
        val domains: Set[Domain] =
          if (!js.has("config")) Set.empty
          else {
            val config = js.getJSONObject("config")
            if (!config.has("domains"))
              Set.empty
            else
              decodeStringSeq('domains)(config).map(str => Domain(Some(str))).toSet
          }
        ClassifiedDomainsConfig(isEnabled = true, domains)
      }
  }
}
