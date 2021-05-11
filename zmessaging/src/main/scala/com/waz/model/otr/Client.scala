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
import com.waz.model.otr.Client.{DeviceClass, DeviceType}
import com.waz.model.{Id, UserId}
import com.waz.utils.crypto.ZSecureRandom
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.collection.breakOut
import scala.util.Try

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

/**
 * Otr client registered on backend, either our own or from other user.
 *
 * @param id
 * @param label - A description of the client, for the self user only
 * @param model - A description of the  client model, for the self user only
 * @param verified - client verification state, updated when user verifies client fingerprint
 * @param deviceClass - The class of the client
 * @param deviceType - The type of client, for the self user only
 * @param regTime - When the client was registered, for the self user only
 */
final case class Client(override val id: ClientId,
                        label:           String = "",
                        model:           String = "",
                        verified:        Verification = Verification.UNKNOWN,
                        deviceClass:     DeviceClass = DeviceClass.Phone,
                        deviceType:      Option[DeviceType] = None,
                        regTime:         Option[Instant] = None) extends Identifiable[ClientId] {

  lazy val isVerified: Boolean = verified == Verification.VERIFIED

  def isLegalHoldDevice: Boolean = deviceClass == DeviceClass.LegalHold

  def updated(c: Client): Client =
    copy(
      label        = if (c.label.isEmpty) label else c.label,
      model        = if (c.model.isEmpty) model else c.model,
      verified     = c.verified.orElse(verified),
      deviceClass  = if (c.deviceClass == DeviceClass.Phone) deviceClass else c.deviceClass,
      deviceType   = c.deviceType.orElse(deviceType),
      regTime      = c.regTime.orElse(regTime)
  )
}

object Client {

  final case class DeviceClass(value: String) extends AnyVal
  object DeviceClass {
    val Phone = DeviceClass("phone")
    val Tablet = DeviceClass("tablet")
    val Desktop = DeviceClass("desktop")
    val LegalHold = DeviceClass("legalhold")
  }

  final case class DeviceType(value: String) extends AnyVal
  object DeviceType {
    val Permanent = DeviceType("permanent")
    val Temporary = DeviceType("temporary")
    val LegalHold = DeviceType("legalhold")
  }

  // To be used to encode client metadata for storage in the database.

  implicit lazy val Encoder: JsonEncoder[Client] = new JsonEncoder[Client] {
    override def apply(v: Client): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("label", v.label)
      o.put("model", v.model)
      o.put("verification", v.verified.name)
      o.put("class", v.deviceClass.value)
      v.deviceType.foreach { d => o.put("type", d.value) }
      v.regTime.foreach { t => o.put("regTime", t.toEpochMilli) }
    }
  }

  // To be used to decode client metadata stored in the database.

  implicit lazy val Decoder: JsonDecoder[Client] = new JsonDecoder[Client] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Client = {
      new Client(
        id = decodeId[ClientId]('id),
        label = 'label,
        model = 'model,
        verified = decodeOptString('verification).fold(Verification.UNKNOWN)(Verification.valueOf),
        deviceClass = DeviceClass(js.getString("class")),
        deviceType = decodeOptString('type).map(DeviceType.apply),
        regTime = 'regTime
      )
    }
  }

  // Previously the device class was stored under "devType" and the device type was not
  // stored at all. This legacy decoder remains as a way to decode old client metadata if the
  // new decoder fails. It can be remove after some time.

  private[otr] implicit lazy val LegacyDecoder: JsonDecoder[Client] = new JsonDecoder[Client] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Client = {
      new Client(
        id = decodeId[ClientId]('id),
        label = 'label,
        model ='model,
        verified = decodeOptString('verification).fold(Verification.UNKNOWN)(Verification.valueOf),
        deviceClass = decodeOptString('devType).fold(DeviceClass.Phone)(DeviceClass.apply),
        regTime ='regTime
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
    override def apply(implicit js: JSONObject): UserClients =
      new UserClients(
        decodeId[UserId]('user),
        decodeClients(js.getJSONArray("clients")).map(c => c.id -> c)(breakOut)
      )

    private def decodeClients(clients: JSONArray): Seq[Client] =
      array(clients, { (arr, index) =>
        val obj = arr.getJSONObject(index)
        Try(Client.Decoder.apply(obj))
          .getOrElse(Client.LegacyDecoder.apply(obj))
      })
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
