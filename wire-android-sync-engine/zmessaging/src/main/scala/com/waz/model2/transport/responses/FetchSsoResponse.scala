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
      decodeOptString('default_sso_code).fold[FetchSsoResponse](SSONotFound)(SSOFound(_))
    }
  }
}
