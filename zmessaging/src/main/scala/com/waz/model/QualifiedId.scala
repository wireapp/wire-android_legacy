package com.waz.model

import com.waz.utils.JsonDecoder.opt
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

final case class QualifiedId(id: UserId, domain: String)

object QualifiedId {
  private val IdFieldName = "id"
  private val DomainFieldName  = "domain"

  implicit val Encoder: JsonEncoder[QualifiedId] =
    JsonEncoder.build(qId => js => {
      js.put(IdFieldName, qId.id.str)
      js.put(DomainFieldName, qId.domain)
    })

  private def decode(js: JSONObject): QualifiedId =
    QualifiedId(UserId(js.getString(IdFieldName)), js.getString(DomainFieldName))

  implicit val Decoder: JsonDecoder[QualifiedId] =
    JsonDecoder.lift(implicit js => decode(js))

  def decodeOpt(s: Symbol)(implicit js: JSONObject): Option[QualifiedId] =
    opt(s, js => decode(js.getJSONObject(s.name)))
}
