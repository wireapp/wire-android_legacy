package com.waz.model.otr

import com.waz.model.{QualifiedId, UserId}
import com.waz.utils.JsonDecoder.decodeStringSeq
import org.json.JSONObject

import scala.collection.JavaConverters._

final case class OtrClientIdMap(entries: Map[UserId, Set[ClientId]]) {
  def userIds: Set[UserId] = entries.keySet
  def isEmpty: Boolean = entries.isEmpty
  def size: Int = entries.size
}

object OtrClientIdMap {
  val Empty: OtrClientIdMap = OtrClientIdMap(Map.empty)

  def apply(entries: Iterable[(UserId, Set[ClientId])]): OtrClientIdMap = OtrClientIdMap(entries.toMap)
  def from(entries: (UserId, Set[ClientId])*): OtrClientIdMap = OtrClientIdMap(entries.toMap)

  def decodeMap(key: Symbol)(implicit js: JSONObject): OtrClientIdMap =
    if (!js.has(key.name) || js.isNull(key.name)) OtrClientIdMap.Empty
    else {
      val mapJs = js.getJSONObject(key.name)
      OtrClientIdMap(
        mapJs.keys.asScala.map { key =>
          UserId(key) -> decodeStringSeq(Symbol(key))(mapJs).map(ClientId(_)).toSet
        }.toMap
      )
    }
}

final case class QOtrClientIdMap(entries: Map[QualifiedId, Set[ClientId]]) {
  def qualifiedIds: Set[QualifiedId] = entries.keySet
  def isEmpty: Boolean = entries.isEmpty
  def size: Int = entries.size
}

object QOtrClientIdMap {
  val Empty: QOtrClientIdMap = QOtrClientIdMap(Map.empty)

  def apply(entries: Iterable[(QualifiedId, Set[ClientId])]): QOtrClientIdMap = QOtrClientIdMap(entries.toMap)
  def from(entries: (QualifiedId, Set[ClientId])*): QOtrClientIdMap = QOtrClientIdMap(entries.toMap)

  def decodeMap(key: Symbol)(implicit js: JSONObject): QOtrClientIdMap =
    if (!js.has(key.name) || js.isNull(key.name)) QOtrClientIdMap.Empty
    else {
      val mapJs = js.getJSONObject(key.name)
      val map = mapJs.keys.asScala.flatMap { domain =>
        val userClientsJs = mapJs.getJSONObject(domain)
        userClientsJs.keys.asScala.map { key =>
          QualifiedId(UserId(key), domain) -> decodeStringSeq(Symbol(key))(userClientsJs).map(ClientId(_)).toSet
        }
      }.toMap
      QOtrClientIdMap(map)
    }
}
