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

import com.waz.api.impl.ErrorResponse
import com.waz.api.{OtrClientType, Verification}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AccountData.Password
import com.waz.model.otr._
import com.waz.model.{RemoteInstant, UserId}
import com.waz.sync.client.OtrClient.{ClientKey, MessageResponse}
import com.waz.sync.otr.OtrSyncHandler.OtrMessage
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.wire.cryptobox.PreKey
import com.wire.messages.nano.Otr
import com.wire.messages.nano.Otr.ClientEntry
import org.json.{JSONArray, JSONObject}

import scala.collection.breakOut
import scala.util.Try

trait OtrClient {
  def loadPreKeys(user: UserId): ErrorOrResponse[Seq[ClientKey]]
  def loadClientPreKey(user: UserId, client: ClientId): ErrorOrResponse[ClientKey]
  def loadPreKeys(users: Map[UserId, Seq[ClientId]]): ErrorOrResponse[Map[UserId, Seq[ClientKey]]]
  def loadClients(): ErrorOrResponse[Seq[Client]]
  def loadClients(user: UserId): ErrorOrResponse[Seq[Client]]
  def loadRemainingPreKeys(id: ClientId): ErrorOrResponse[Seq[Int]]
  def deleteClient(id: ClientId, password: Option[Password]): ErrorOrResponse[Unit]
  def postClient(userId: UserId, client: Client, lastKey: PreKey, keys: Seq[PreKey], password: Option[Password]): ErrorOrResponse[Client]
  def postClientLabel(id: ClientId, label: String): ErrorOrResponse[Unit]
  def updateKeys(id: ClientId, prekeys: Option[Seq[PreKey]] = None, lastKey: Option[PreKey] = None, sigKey: Option[SignalingKey] = None): ErrorOrResponse[Unit]
  def broadcastMessage(content: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse]
}

class OtrClientImpl(implicit
                    urlCreator: UrlCreator,
                    httpClient: HttpClient,
                    authRequestInterceptor: AuthRequestInterceptor) extends OtrClient with DerivedLogTag {

  import HttpClient.AutoDerivationOld._
  import HttpClient.dsl._
  import MessagesClient.OtrMessageSerializer
  import OtrClient._
  import com.waz.threading.Threading.Implicits.Background

  private[waz] val PermanentClient = true // for testing

  private implicit val PreKeysResponseDeserializer: RawBodyDeserializer[PreKeysResponse] =
    RawBodyDeserializer[JSONObject].map(json => PreKeysResponse.unapply(JsonObjectResponse(json)).get)

  private implicit val ClientsDeserializer: RawBodyDeserializer[Seq[Client]] =
    RawBodyDeserializer[JSONArray].map(json => ClientsResponse.unapply(JsonArrayResponse(json)).get)

  //TODO We have to introduce basic deserializers for the seq
  private implicit val RemainingPreKeysDeserializer: RawBodyDeserializer[Seq[Int]] =
    RawBodyDeserializer[JSONArray].map(json => RemainingPreKeysResponse.unapply(JsonArrayResponse(json)).get)

  override def loadPreKeys(user: UserId): ErrorOrResponse[Seq[ClientKey]] = {
    Request.Get(relativePath = userPreKeysPath(user))
      .withResultType[UserPreKeysResponse]
      .withErrorType[ErrorResponse]
      .executeSafe(_.keys)
  }

  override def loadClientPreKey(user: UserId, client: ClientId): ErrorOrResponse[ClientKey] = {
    Request.Get(relativePath = clientPreKeyPath(user, client))
      .withResultType[ClientKey]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadPreKeys(users: Map[UserId, Seq[ClientId]]): ErrorOrResponse[Map[UserId, Seq[ClientKey]]] = {
    // TODO: request accepts up to 128 clients, we should make sure not to send more
    val data = JsonEncoder { o =>
      users.foreach { case (u, cs) =>
        o.put(u.str, JsonEncoder.arrString(cs.map(_.str)))
      }
    }
    Request.Post(relativePath = prekeysPath, body = data)
      .withResultType[PreKeysResponse]
      .withErrorType[ErrorResponse]
      .executeSafe(_.toMap)
  }

  override def loadClients(): ErrorOrResponse[Seq[Client]] = {
    Request.Get(relativePath = clientsPath)
      .withResultType[Seq[Client]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadClients(user: UserId): ErrorOrResponse[Seq[Client]] = {
    Request.Get(relativePath = userClientsPath(user))
      .withResultType[Seq[Client]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadRemainingPreKeys(id: ClientId): ErrorOrResponse[Seq[Int]] = {
    Request.Get(relativePath = clientKeyIdsPath(id))
      .withResultType[Seq[Int]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def deleteClient(id: ClientId, password: Option[Password]): ErrorOrResponse[Unit] =
    Request.Delete(
      relativePath = clientPath(id),
      body = JsonEncoder { o => password.foreach(pwd => o.put("password", pwd.str)) }
    ).withResultType[Unit].withErrorType[ErrorResponse].executeSafe

  override def postClient(userId: UserId, client: Client, lastKey: PreKey, keys: Seq[PreKey], password: Option[Password]): ErrorOrResponse[Client] = {
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

    Request.Post(relativePath = clientsPath, body = data)
      .withResultType[Client]
      .withErrorType[ErrorResponse]
      .executeSafe(_.copy(signalingKey = client.signalingKey, verified = Verification.VERIFIED)) //TODO Maybe we can add description for this?
  }

  override def postClientLabel(id: ClientId, label: String): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o =>
      o.put("prekeys", new JSONArray)
      o.put("label", label)
    }
    Request.Put(relativePath = clientPath(id), body = data)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updateKeys(id: ClientId, prekeys: Option[Seq[PreKey]] = None, lastKey: Option[PreKey] = None, sigKey: Option[SignalingKey] = None): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o =>
      lastKey.foreach(k => o.put("lastkey", JsonEncoder.encode(k)))
      sigKey.foreach(k => o.put("sigkeys", JsonEncoder.encode(k)))
      prekeys.foreach(ks => o.put("prekeys", JsonEncoder.arr(ks)))
    }
    Request.Put(relativePath = clientPath(id), body = data)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def broadcastMessage(content: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse] = {
    Request
      .Post(
        relativePath = BroadcastPath,
        queryParameters = queryParameters("ignore_missing" -> ignoreMissing),
        body = content
      )
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.PreconditionFailed)
      .withResultType[Response[ClientMismatch]]
      .withErrorType[ErrorResponse]
      .executeSafe { case Response(code, _, body) =>
        if (code == ResponseCode.PreconditionFailed) MessageResponse.Failure(body)
        else MessageResponse.Success(body)
      }

  }
}

object OtrClient extends DerivedLogTag {

  val clientsPath = "/clients"
  val prekeysPath = "/users/prekeys"
  val BroadcastPath = "/broadcast/otr/messages"

  def clientPath(id: ClientId) = s"/clients/$id"
  def clientKeyIdsPath(id: ClientId) = s"/clients/$id/prekeys"
  def userPreKeysPath(user: UserId) = s"/users/$user/prekeys"
  def userClientsPath(user: UserId) = s"/users/$user/clients"
  def clientPreKeyPath(user: UserId, client: ClientId) = s"/users/$user/prekeys/$client"

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
          clients foreach { case (c, msg) => u.put(c.str, AESUtils.base64(msg)) }
        })
      }
    }
  }

  implicit lazy val PreKeyDecoder: JsonDecoder[PreKey] = JsonDecoder.lift { implicit js =>
    val keyStr: String = 'key
    new PreKey('id, AESUtils.base64(keyStr))
  }

  implicit lazy val ClientDecoder: JsonDecoder[ClientKey] = JsonDecoder.lift { implicit js =>
    (decodeId[ClientId]('client), JsonDecoder[PreKey]('prekey))
  }

  case class UserPreKeysResponse(userId: UserId, keys: Seq[ClientKey])

  object UserPreKeysResponse {
    implicit def UserPreKeysResponseDecoder: JsonDecoder[UserPreKeysResponse] = JsonDecoder.lift { implicit js =>
      UserPreKeysResponse('user: UserId, JsonDecoder.decodeSeq('clients)(js, ClientDecoder))
    }
  }


  //TODO Remove this. Introduce JSONDecoder for the Map
  type PreKeysResponse = Seq[(UserId, Seq[ClientKey])]
  object PreKeysResponse {
    import scala.collection.JavaConverters._
    def unapply(content: ResponseContent): Option[PreKeysResponse] = content match {
      case JsonObjectResponse(js) =>
        Try {
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

  object ClientsResponse {

    def client(implicit js: JSONObject) = Client(decodeId[ClientId]('id), 'label, 'model, decodeOptUtcDate('time).map(_.instant), opt[Location]('location), 'address, devType = decodeOptString('class).fold(OtrClientType.PHONE)(OtrClientType.fromDeviceClass))

    def unapply(content: ResponseContent): Option[Seq[Client]] = content match {
      case JsonObjectResponse(js) => Try(Seq(client(js))).toOption
      case JsonArrayResponse(arr) => Try(JsonDecoder.array(arr, { (arr, i) => client(arr.getJSONObject(i)) })).toOption
      case _ => None
    }
  }

  object RemainingPreKeysResponse {
    def unapply(content: ResponseContent): Option[Seq[Int]] = content match {
      case JsonArrayResponse(arr) => Try(JsonDecoder.array(arr, _.getString(_).toInt)).toOption
      case _ => None
    }
  }

  sealed trait MessageResponse {
    def mismatch: ClientMismatch

    def deleted: Map[UserId, Seq[ClientId]] =
      mismatch.deleted

    def missing: Map[UserId, Seq[ClientId]] =
      mismatch.missing
  }
  object MessageResponse {
    case class Success(mismatch: ClientMismatch) extends MessageResponse
    case class Failure(mismatch: ClientMismatch) extends MessageResponse
  }

  case class ClientMismatch(redundant: Map[UserId, Seq[ClientId]] = Map.empty,
                            missing:   Map[UserId, Seq[ClientId]] = Map.empty,
                            deleted:   Map[UserId, Seq[ClientId]] = Map.empty,
                            time: RemoteInstant)

  object ClientMismatch {
    implicit lazy val Decoder: JsonDecoder[ClientMismatch] = new JsonDecoder[ClientMismatch] {
      import JsonDecoder._

      import scala.collection.JavaConverters._

      def decodeMap(key: Symbol)(implicit js: JSONObject): Map[UserId, Seq[ClientId]] = {
        if (!js.has(key.name) || js.isNull(key.name)) Map.empty
        else {
          val mapJs = js.getJSONObject(key.name)
          mapJs.keys().asScala.map { key =>
            UserId(key) -> decodeStringSeq(Symbol(key))(mapJs).map(ClientId(_))
          }.toMap
        }

      }

      override def apply(implicit js: JSONObject): ClientMismatch =
        ClientMismatch(decodeMap('redundant),
          decodeMap('missing),
          decodeMap('deleted),
          decodeOptUtcDate('time).map(t => RemoteInstant.ofEpochMilli(t.getTime)).getOrElse(RemoteInstant.Epoch))
    }
  }

}
