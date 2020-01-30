package com.waz.model2.transport.responses

import com.waz.utils.JsonDecoder
import org.json.JSONObject

sealed class DomainVerificationResponse
object DomainNotFound extends DomainVerificationResponse
case class DomainSuccessful(configFileUrl: String) extends DomainVerificationResponse

object DomainVerificationResponse {

  implicit val DomainVerificationResponseDecoder: JsonDecoder[DomainVerificationResponse] = new JsonDecoder[DomainVerificationResponse] {
    override def apply(implicit js: JSONObject): DomainVerificationResponse = {
      import JsonDecoder._
      val configUrl = decodeOptString('config_json_url)
      configUrl match {
        case Some(url) => DomainSuccessful(url)
        case None => DomainNotFound
      }
    }
  }
}
