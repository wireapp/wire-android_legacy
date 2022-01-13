package com.waz.model

import com.waz.utils.JsonDecoder.opt
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.zms.BuildConfig
import org.json.{JSONArray, JSONObject}

final case class RConvQualifiedId(id: RConvId, domain: String) {
  def hasDomain: Boolean = domain.nonEmpty

  def str: String = if(BuildConfig.FEDERATION_USER_DISCOVERY && domain.nonEmpty)
    s"$id@$domain"
  else id.str
}

object RConvQualifiedId {
  def apply(id: RConvId): RConvQualifiedId = RConvQualifiedId(id, "")
  def apply(id: RConvId, domain: Domain): RConvQualifiedId = RConvQualifiedId(id, domain.str)

  private val IdFieldName = "id"
  private val DomainFieldName  = "domain"

  implicit val Encoder: JsonEncoder[RConvQualifiedId] =
    JsonEncoder.build(qId => js => {
      js.put(IdFieldName, qId.id.str)
      js.put(DomainFieldName, qId.domain)
    })

  private def decode(js: JSONObject): RConvQualifiedId =
    RConvQualifiedId(RConvId(js.getString(IdFieldName)), js.getString(DomainFieldName))

  implicit val Decoder: JsonDecoder[RConvQualifiedId] =
    JsonDecoder.lift(implicit js => decode(js))

  def decodeOpt(s: Symbol)(implicit js: JSONObject): Option[RConvQualifiedId] =
    opt(s, js => decode(js.getJSONObject(s.name)))

  def encode(qIds: Set[RConvQualifiedId]): JSONArray =
    JsonEncoder.array(qIds) { case (arr, qid) => arr.put(RConvQualifiedId.Encoder(qid)) }
}
