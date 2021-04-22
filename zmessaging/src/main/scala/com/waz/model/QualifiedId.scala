package com.waz.model

import com.waz.utils.JsonDecoder.opt
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

final case class QualifiedId(id: UserId, domain: String)

object QualifiedId {
  implicit val Encoder: JsonEncoder[QualifiedId] = JsonEncoder.build(qId => js => {
    js.put("id", qId.id.str)
    js.put("domain", qId.domain)
  })

  private def decode(js: JSONObject): QualifiedId =
    QualifiedId(UserId(js.getString("id")), js.getString("domain"))

  implicit val Decoder: JsonDecoder[QualifiedId] = JsonDecoder.lift(implicit js => decode(js))

  def decodeOpt(s: Symbol)(implicit js: JSONObject): Option[QualifiedId] = opt(s, js => decode(js.getJSONObject(s.name)))
}
