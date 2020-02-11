package com.waz.model2.transport.responses

import com.waz.utils.JsonDecoder
import org.json.JSONObject

sealed class FetchSsoResponse
object SSONotFound extends FetchSsoResponse
case class SSOFound(sso: String) extends FetchSsoResponse

object FetchSsoResponse {

  implicit val fetchSsoResponseDecoder: JsonDecoder[FetchSsoResponse] = new JsonDecoder[FetchSsoResponse] {
    override def apply(implicit js: JSONObject): FetchSsoResponse = {
      import JsonDecoder._
      val configUrl = decodeOptString('default_sso_code)
      configUrl match {
        case Some(sso) => SSOFound(sso)
        case None => SSONotFound
      }
    }
  }
}
