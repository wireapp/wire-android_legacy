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
package com.waz.sync.client

import java.util.Date

import android.util.Base64
import com.waz.ZLog._
import com.waz.api.{OtrClientType, Verification}
import com.waz.model.AccountData.Password
import com.waz.model.otr._
import com.waz.model.UserId
import com.waz.sync.otr.OtrMessage
import com.waz.utils._
import com.waz.znet.Response.{HttpStatus, SuccessHttpStatus}
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import com.wire.cryptobox.PreKey
import com.wire.messages.nano.Otr
import com.wire.messages.nano.Otr.ClientEntry
import org.json.{JSONArray, JSONObject}

import scala.collection.breakOut

class OtrClient(netClient: ZNetClient) {
  import OtrClient._
  import com.waz.threading.Threading.Implicits.Background

  private[waz] val PermanentClient = true // for testing

  def loadPreKeys(user: UserId): ErrorOrResponse[Seq[ClientKey]] =
    netClient.withErrorHandling(s"loadPreKeys", Request.Get(userPreKeysPath(user))) {
      case Response(SuccessHttpStatus(), UserPreKeysResponse(`user`, clients), _) => clients
    }

  def loadClientPreKey(user: UserId, client: ClientId): ErrorOrResponse[ClientKey] =
    netClient.withErrorHandling("loadClientPreKey", Request.Get(clientPreKeyPath(user, client))) {
      case Response(SuccessHttpStatus(), ClientPreKeyResponse(id, key), _) => id -> key
    }

  def loadPreKeys(users: Map[UserId, Seq[ClientId]]): ErrorOrResponse[Map[UserId, Seq[ClientKey]]] = {
    // TODO: request accepts up to 128 clients, we should make sure not to send more
    val data = JsonEncoder { o =>
      users foreach { case (u, cs) =>
        o.put(u.str, JsonEncoder.arrString(cs.map(_.str)))
      }
    }

    verbose(s"loadPreKeys: $users")
    netClient.withErrorHandling("loadPreKeys", Request.Post(prekeysPath, data)) {
      case Response(SuccessHttpStatus(), PreKeysResponse(map), _) => map.toMap
    }
  }

  def loadClients(): ErrorOrResponse[Seq[Client]] =
    netClient.withErrorHandling("loadClients", Request.Get(clientsPath)) {
      case Response(SuccessHttpStatus(), ClientsResponse(clients), _) => clients
    }

  def loadClients(user: UserId): ErrorOrResponse[Seq[Client]] =
    netClient.withErrorHandling("loadClients", Request.Get(userClientsPath(user))) {
      case Response(SuccessHttpStatus(), ClientsResponse(clients), _) => clients
    }

  def loadRemainingPreKeys(id: ClientId): ErrorOrResponse[Seq[Int]] =
    netClient.withErrorHandling("loadRemainingPreKeys", Request.Get(clientKeyIdsPath(id))) {
      case Response(SuccessHttpStatus(), RemainingPreKeysResponse(ids), _) => ids
    }

  def deleteClient(id: ClientId, password: Password): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o => o.put("password", password.str) }
    netClient.withErrorHandling("deleteClient", Request.Delete(clientPath(id), Some(data))) {
      case Response(SuccessHttpStatus(), _, _) => ()
    }
  }

  def postClient(userId: UserId, client: Client, lastKey: PreKey, keys: Seq[PreKey], password: Option[Password]): ErrorOrResponse[Client] = {
    val data = JsonEncoder { o =>
      o.put("lastkey", JsonEncoder.encode(lastKey)(PreKeyEncoder))
      client.signalingKey foreach { sk => o.put("sigkeys", JsonEncoder.encode(sk)) }
      o.put("prekeys", JsonEncoder.arr(keys)(PreKeyEncoder))
      o.put("type", if (PermanentClient) "permanent" else "temporary")
      o.put("label", client.label)
      o.put("model", client.model)
      o.put("class", client.devType.deviceClass)
      o.put("cookie", userId.str)
      password.map(_.str).foreach(o.put("password", _))
    }
    netClient.withErrorHandling("postClient", Request.Post(clientsPath, data)) {
      case Response(SuccessHttpStatus(), ClientsResponse(Seq(c)), _) => c.copy(signalingKey = client.signalingKey, verified = Verification.VERIFIED)
    }
  }

  def postClientLabel(id: ClientId, label: String): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o =>
      o.put("prekeys", new JSONArray)
      o.put("label", label)
    }
    netClient.withErrorHandling("postClientLabel", Request.Put(clientPath(id), data)) {
      case Response(SuccessHttpStatus(), _, _) => ()
    }
  }

  def updateKeys(id: ClientId, prekeys: Option[Seq[PreKey]] = None, lastKey: Option[PreKey] = None, sigKey: Option[SignalingKey] = None): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o =>
      lastKey.foreach(k => o.put("lastkey", JsonEncoder.encode(k)))
      sigKey.foreach(k => o.put("sigkeys", JsonEncoder.encode(k)))
      prekeys.foreach(ks => o.put("prekeys", JsonEncoder.arr(ks)))
    }
    netClient.withErrorHandling("postClient", Request.Put(clientPath(id), data)) {
      case Response(SuccessHttpStatus(), _, _) => ()
    }
  }

  def broadcastMessage(content: OtrMessage, ignoreMissing: Boolean, receivers: Set[UserId] = Set.empty): ErrorOrResponse[MessageResponse] =
    netClient.withErrorHandling("broadcastMessage", Request.Post(broadcastMessagesPath(ignoreMissing, receivers), content)) {
      case Response(SuccessHttpStatus(), ClientMismatchResponse(mismatch), _) => MessageResponse.Success(mismatch)
      case Response(HttpStatus(Response.Status.PreconditionFailed, _), ClientMismatchResponse(mismatch), _) => MessageResponse.Failure(mismatch)
    }
}

object OtrClient {
  private implicit val tag: LogTag = logTagFor[OtrClient]

  val clientsPath = "/clients"
  val prekeysPath = "/users/prekeys"
  val broadcastPath = "/broadcast/otr/messages"

  def clientPath(id: ClientId) = s"/clients/$id"
  def clientKeyIdsPath(id: ClientId) = s"/clients/$id/prekeys"
  def userPreKeysPath(user: UserId) = s"/users/$user/prekeys"
  def userClientsPath(user: UserId) = s"/users/$user/clients"
  def clientPreKeyPath(user: UserId, client: ClientId) = s"/users/$user/prekeys/$client"

  def broadcastMessagesPath(ignoreMissing: Boolean, receivers: Set[UserId] = Set.empty) =
    if (ignoreMissing) Request.query(broadcastPath, "ignore_missing" -> "true")
    else if (receivers.isEmpty) ""
    else Request.query(broadcastPath, "report_missing" -> receivers.map(_.str).mkString(","))

  import JsonDecoder._

  type ClientKey = (ClientId, PreKey)

  final def userId(id: UserId) = {
    val user = new Otr.UserId
    user.uuid = id.bytes
    user
  }

  final def clientId(id: ClientId) = {
    val client = new Otr.ClientId
    client.client = id.longId
    client
  }

  case class EncryptedContent(content: Map[UserId, Map[ClientId, Array[Byte]]]) {
    def isEmpty = content.isEmpty
    def nonEmpty = content.nonEmpty
    lazy val estimatedSize = content.valuesIterator.map { cs => 16 + cs.valuesIterator.map(_.length + 8).sum }.sum

    lazy val userEntries: Array[Otr.UserEntry] =
      content.map {
        case (user, cs) =>
          val entry = new Otr.UserEntry
          entry.user = userId(user)
          entry.clients = cs.map {
            case (c, msg) =>
              val ce = new ClientEntry
              ce.client = clientId(c)
              ce.text = msg
              ce
          } (breakOut)
          entry
      } (breakOut)
  }

  object EncryptedContent {
    val Empty = EncryptedContent(Map.empty)
  }

  lazy val EncryptedContentEncoder: JsonEncoder[EncryptedContent] = new JsonEncoder[EncryptedContent] {
    override def apply(content: EncryptedContent): JSONObject = JsonEncoder { o =>
      content.content foreach { case (user, clients) =>
        o.put(user.str, JsonEncoder { u =>
          clients foreach { case (c, msg) => u.put(c.str, Base64.encodeToString(msg, Base64.NO_WRAP)) }
        })
      }
    }
  }

  implicit lazy val PreKeyDecoder: JsonDecoder[PreKey] = new JsonDecoder[PreKey] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): PreKey = new PreKey('id, Base64.decode('key, Base64.DEFAULT))
  }

  lazy val ClientDecoder: JsonDecoder[ClientKey] = new JsonDecoder[ClientKey] {
    def apply(implicit js: JSONObject) = (decodeId[ClientId]('client), JsonDecoder[PreKey]('prekey))
  }

  object UserPreKeysResponse {

    def unapply(content: ResponseContent): Option[(UserId, Seq[ClientKey])] = content match {
      case JsonObjectResponse(js) =>
        implicit val jsObj = js
        LoggedTry.local { ('user: UserId, JsonDecoder.decodeSeq('clients)(js, ClientDecoder)) } .toOption
      case _ => None
    }
  }

  object PreKeysResponse {
    import scala.collection.JavaConverters._
    def unapply(content: ResponseContent): Option[Seq[(UserId, Seq[ClientKey])]] = content match {
      case JsonObjectResponse(js) =>
        LoggedTry.local {
          js.keys().asInstanceOf[java.util.Iterator[String]].asScala.map { userId =>
            val cs = js.getJSONObject(userId)
            val clients = cs.keys().asInstanceOf[java.util.Iterator[String]].asScala.map { clientId =>
              if (cs.isNull(clientId)) None else Some(ClientId(clientId) -> PreKeyDecoder(cs.getJSONObject(clientId)))
            }
            UserId(userId) -> clients.flatten.toSeq
          } .filter(_._2.nonEmpty).toSeq
        } .toOption
      case _ => None
    }
  }

  object ClientPreKeyResponse {
    def unapply(content: ResponseContent): Option[ClientKey] = content match {
      case JsonObjectResponse(js) => LoggedTry.local(ClientDecoder(js)).toOption
      case _ => None
    }
  }

  object ClientsResponse {

    def client(implicit js: JSONObject) = Client(decodeId[ClientId]('id), 'label, 'model, decodeOptUtcDate('time).map(_.instant), opt[Location]('location), 'address, devType = decodeOptString('class).fold(OtrClientType.PHONE)(OtrClientType.fromDeviceClass))

    def unapply(content: ResponseContent): Option[Seq[Client]] = content match {
      case JsonObjectResponse(js) => LoggedTry(Seq(client(js))).toOption
      case JsonArrayResponse(arr) => LoggedTry.local(JsonDecoder.array(arr, { (arr, i) => client(arr.getJSONObject(i)) })).toOption
      case _ => None
    }
  }

  object RemainingPreKeysResponse {
    def unapply(content: ResponseContent): Option[Seq[Int]] = content match {
      case JsonArrayResponse(arr) => LoggedTry.local(JsonDecoder.array(arr, _.getString(_).toInt)).toOption
      case _ => None
    }
  }

  sealed trait MessageResponse { def mismatch: ClientMismatch }
  object MessageResponse {
    case class Success(mismatch: ClientMismatch) extends MessageResponse
    case class Failure(mismatch: ClientMismatch) extends MessageResponse
  }

  case class ClientMismatch(redundant: Map[UserId, Seq[ClientId]], missing: Map[UserId, Seq[ClientId]], deleted: Map[UserId, Seq[ClientId]], time: Date)

  object ClientMismatch {
    def apply(time: Date) = new ClientMismatch(Map.empty, Map.empty, Map.empty, time)

    implicit lazy val Decoder: JsonDecoder[ClientMismatch] = new JsonDecoder[ClientMismatch] {
      import JsonDecoder._

      import scala.collection.JavaConverters._

      def decodeMap(key: Symbol)(implicit js: JSONObject): Map[UserId, Seq[ClientId]] = {
        if (!js.has(key.name) || js.isNull(key.name)) Map.empty
        else {
          val mapJs = js.getJSONObject(key.name)
          mapJs.keys().asInstanceOf[java.util.Iterator[String]].asScala.map { key =>
            UserId(key) -> decodeStringSeq(Symbol(key))(mapJs).map(ClientId(_))
          }.toMap
        }

      }

      override def apply(implicit js: JSONObject): ClientMismatch = ClientMismatch(decodeMap('redundant), decodeMap('missing), decodeMap('deleted), decodeOptUtcDate('time).getOrElse(new Date))
    }
  }

  object ClientMismatchResponse {
    def unapply(content: ResponseContent): Option[ClientMismatch] = content match {
      case JsonObjectResponse(js) => LoggedTry.local(ClientMismatch.Decoder(js)).toOption
      case _ => None
    }
  }
}
