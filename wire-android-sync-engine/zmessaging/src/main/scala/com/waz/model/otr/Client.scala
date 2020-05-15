/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model.otr

import java.math.BigInteger

import com.waz.api.{OtrClientType, Verification}
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.{Id, UserId}
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.collection.breakOut

case class ClientId(str: String) {
  def longId = new BigInteger(str, 16).longValue()
  override def toString: String = str
}

object ClientId {

  implicit val id: Id[ClientId] = new Id[ClientId] {
    override def random(): ClientId = ClientId(ZSecureRandom.nextLong().toHexString)
    override def decode(str: String): ClientId = ClientId(str)
    override def encode(id: ClientId): String = id.str
  }

  def apply() = id.random()

  def opt(id: String) = Option(id).filter(_.nonEmpty).map(ClientId(_))
}

case class Location(lon: Double, lat: Double, name: String) {

  def hasName = name != ""
  def getName = if (hasName) name else s"$lat, $lon"
}

object Location {
  val Empty = Location(0, 0, "")

  implicit lazy val Encoder: JsonEncoder[Location] = new JsonEncoder[Location] {
    override def apply(v: Location): JSONObject = JsonEncoder { o =>
      o.put("lon", v.lon)
      o.put("lat", v.lat)
      o.put("name", v.name)
    }
  }

  implicit lazy val Decoder: JsonDecoder[Location] = new JsonDecoder[Location] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Location = new Location('lon, 'lat, 'name)
  }
}

/**
 * Otr client registered on backend, either our own or from other user.
 *
 * @param id
 * @param label
 * @param signalingKey - will only be set for current device
 * @param verified - client verification state, updated when user verifies client fingerprint
 */
case class Client(override val id: ClientId,
                  label:           String,
                  model:           String = "",
                  regTime:         Option[Instant] = None,
                  regLocation:     Option[Location] = None,
                  regIpAddress:    Option[String] = None,
                  signalingKey:    Option[SignalingKey] = None,
                  verified:        Verification = Verification.UNKNOWN,
                  devType:         OtrClientType = OtrClientType.PHONE) extends Identifiable[ClientId] {

  def isVerified = verified == Verification.VERIFIED

  def updated(c: Client) = {
    val location = (regLocation, c.regLocation) match {
      case (Some(loc), Some(l)) if loc.lat == l.lat && loc.lon == l.lon => Some(loc)
      case (_, loc @ Some(_)) => loc
      case (loc, _) => loc
    }
    copy (
      label = if (c.label.isEmpty) label else c.label,
      model = if (c.model.isEmpty) model else c.model,
      regTime = c.regTime.orElse(regTime),
      regLocation = location,
      regIpAddress = c.regIpAddress.orElse(regIpAddress),
      signalingKey = c.signalingKey.orElse(signalingKey),
      verified = c.verified.orElse(verified),
      devType = if (c.devType == OtrClientType.PHONE) devType else c.devType
    )
  }
}

object Client {

  implicit lazy val Encoder: JsonEncoder[Client] = new JsonEncoder[Client] {
    override def apply(v: Client): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("label", v.label)
      o.put("model", v.model)
      v.regTime foreach { t => o.put("regTime", t.toEpochMilli) }
      v.regLocation foreach { l => o.put("regLocation", JsonEncoder.encode(l)) }
      v.regIpAddress foreach { o.put("regIpAddress", _) }
      v.signalingKey foreach { sk => o.put("signalingKey", JsonEncoder.encode(sk)) }
      o.put("verification", v.verified.name)
      o.put("devType", v.devType.deviceClass)
    }
  }

  implicit lazy val Decoder: JsonDecoder[Client] = new JsonDecoder[Client] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Client = {
      new Client(decodeId[ClientId]('id), 'label, 'model, 'regTime, opt[Location]('regLocation), 'regIpAddress, opt[SignalingKey]('signalingKey),
        decodeOptString('verification).fold(Verification.UNKNOWN)(Verification.valueOf),
        decodeOptString('devType).fold(OtrClientType.PHONE)(OtrClientType.fromDeviceClass)
      )
    }
  }
}

case class UserClients(user: UserId, clients: Map[ClientId, Client]) extends Identifiable[UserId] {
  override val id: UserId = user
  def -(clientId: ClientId) = UserClients(user, clients - clientId)
}

object UserClients {

  implicit lazy val Encoder: JsonEncoder[UserClients] = new JsonEncoder[UserClients] {
    override def apply(v: UserClients): JSONObject = JsonEncoder { o =>
      o.put("user", v.user.str)
      o.put("clients", JsonEncoder.arr(v.clients.values.toSeq))
    }
  }

  implicit lazy val Decoder: JsonDecoder[UserClients] = new JsonDecoder[UserClients] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): UserClients = new UserClients(decodeId[UserId]('user), decodeSeq[Client]('clients).map(c => c.id -> c)(breakOut))
  }


  implicit object UserClientsDao extends Dao[UserClients, UserId] {

    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.user)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("Clients", Id, Data)

    override def apply(implicit cursor: DBCursor): UserClients = JsonDecoder.decode(Data)(Decoder)

    def find(ids: Traversable[UserId])(implicit db: DB): Vector[UserClients] =
      if (ids.isEmpty) Vector.empty
      else list(db.query(table.name, null, s"${Id.name} in (${ids.map(_.str).mkString("'", "','", "'")})", null, null, null, null))
  }
}
