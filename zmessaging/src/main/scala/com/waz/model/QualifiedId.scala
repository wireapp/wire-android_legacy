package com.waz.model

import com.waz.utils.JsonDecoder.opt
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.{JSONArray, JSONObject}

final case class QualifiedId(id: UserId, domain: String) {
  def hasDomain: Boolean = domain.nonEmpty

  def str: String = if (domain.nonEmpty) s"$id@$domain" else id.str
}

object QualifiedId {
  def apply(userId: UserId): QualifiedId = QualifiedId(userId, "")
  def apply(userId: UserId, domain: Domain): QualifiedId = QualifiedId(userId, domain.str)
  def apply(userId: UserId, domain: Domain, federationSupported: Boolean): QualifiedId = if(federationSupported) QualifiedId(userId, domain.str) else QualifiedId(userId)

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

  def encode(qIds: Set[QualifiedId]): JSONArray =
      JsonEncoder.array(qIds) { case (arr, qid) => arr.put(QualifiedId.Encoder(qid)) }
}
