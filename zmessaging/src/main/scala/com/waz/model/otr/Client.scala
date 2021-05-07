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

import com.waz.api.Verification
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.otr.Client.DeviceClass
import com.waz.model.{Id, UserId}
import com.waz.utils.JsonDecoder.{decodeId, decodeOptString, decodeOptUtcDate, opt}
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.collection.breakOut

final case class ClientId(str: String) extends AnyVal {
  def longId: Long = new BigInteger(str, 16).longValue()
  override def toString: String = str
}

object ClientId {

  implicit val id: Id[ClientId] = new Id[ClientId] {
    override def random(): ClientId = ClientId(ZSecureRandom.nextLong().toHexString)
    override def decode(str: String): ClientId = ClientId(str)
    override def encode(id: ClientId): String = id.str
  }

  def apply(): ClientId = id.random()

  def opt(id: String): Option[ClientId] = Option(id).filter(_.nonEmpty).map(ClientId(_))
}

final case class Location(lon: Double, lat: Double, name: String) {
  def hasName = name != ""
}

object Location {
  val Empty: Location = Location(0, 0, "")

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
 * @param verified - client verification state, updated when user verifies client fingerprint
 */
final case class Client(override val id: ClientId,
                        label:           String,
                        model:           String = "",
                        regTime:         Option[Instant] = None,
                        regLocation:     Option[Location] = None,
                        verified:        Verification = Verification.UNKNOWN,
                        deviceClass:     DeviceClass = DeviceClass.Phone) extends Identifiable[ClientId] {

  lazy val isVerified: Boolean = verified == Verification.VERIFIED

  def isLegalHoldDevice: Boolean = deviceClass == DeviceClass.LegalHold

  def updated(c: Client): Client = {
    val location = (regLocation, c.regLocation) match {
      case (Some(loc), Some(l)) if loc.lat == l.lat && loc.lon == l.lon => Some(loc)
      case (_, loc @ Some(_)) => loc
      case (loc, _) => loc
    }
    copy(
      label        = if (c.label.isEmpty) label else c.label,
      model        = if (c.model.isEmpty) model else c.model,
      regTime      = c.regTime.orElse(regTime),
      regLocation  = location,
      verified     = c.verified.orElse(verified),
      deviceClass  = if (c.deviceClass == DeviceClass.Phone) deviceClass else c.deviceClass
    )
  }
}

object Client {

  final case class DeviceClass(value: String)
  object DeviceClass {
    val Phone = DeviceClass("phone")
    val Tablet = DeviceClass("tablet")
    val Desktop = DeviceClass("desktop")
    val LegalHold = DeviceClass("legalhold")
  }

  implicit lazy val Encoder: JsonEncoder[Client] = new JsonEncoder[Client] {
    override def apply(v: Client): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("label", v.label)
      o.put("model", v.model)
      v.regTime foreach { t => o.put("regTime", t.toEpochMilli) }
      v.regLocation foreach { l => o.put("regLocation", JsonEncoder.encode(l)) }
      o.put("verification", v.verified.name)
      o.put("class", v.deviceClass.value)
    }
  }

  implicit lazy val Decoder: JsonDecoder[Client] = new JsonDecoder[Client] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Client = {
      new Client(
        decodeId[ClientId]('id),
        'label,
        'model,
        'regTime,
        opt[Location]('regLocation),
        decodeOptString('verification).fold(Verification.UNKNOWN)(Verification.valueOf),
        decodeOptString('class).fold(DeviceClass.Phone)(DeviceClass.apply)
      )
    }
  }
}

final case class UserClients(user: UserId, clients: Map[ClientId, Client]) extends Identifiable[UserId] {
  override val id: UserId = user
  def -(clientId: ClientId): UserClients = UserClients(user, clients - clientId)
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
