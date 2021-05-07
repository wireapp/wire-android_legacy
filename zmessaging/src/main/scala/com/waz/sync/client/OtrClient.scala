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

import java.nio.ByteBuffer
import java.util.UUID

import com.google.protobuf.ByteString
import com.waz.api.impl.ErrorResponse
import com.waz.api.Verification
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.otr.Client.{DeviceClass, DeviceType}
import com.waz.model.otr._
import com.waz.model.{QualifiedId, RemoteInstant, UserId}
import com.waz.sync.client.OtrClient.{ClientKey, MessageResponse}
import com.waz.sync.otr.OtrSyncHandler.OtrMessage
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import com.wire.cryptobox.PreKey
import com.wire.messages.Otr
import org.json.{JSONArray, JSONObject}

import scala.collection.breakOut
import scala.util.{Failure, Success, Try}

trait OtrClient {
  def loadPreKeys(user: UserId): ErrorOrResponse[Seq[ClientKey]]
  def loadClientPreKey(user: UserId, client: ClientId): ErrorOrResponse[ClientKey]
  def loadPreKeys(users: Map[UserId, Seq[ClientId]]): ErrorOrResponse[Map[UserId, Seq[ClientKey]]]
  def loadClients(): ErrorOrResponse[Seq[Client]]
  def loadClients(user: UserId): ErrorOrResponse[Seq[Client]]
  def loadClients(users: Set[QualifiedId]): ErrorOrResponse[Map[QualifiedId, Seq[Client]]]
  def loadRemainingPreKeys(id: ClientId): ErrorOrResponse[Seq[Int]]
  def deleteClient(id: ClientId, password: Option[Password]): ErrorOrResponse[Unit]
  def postClient(userId: UserId, client: Client, lastKey: PreKey, keys: Seq[PreKey], password: Option[Password]): ErrorOrResponse[Client]
  def postClientLabel(id: ClientId, label: String): ErrorOrResponse[Unit]
  def updateKeys(id: ClientId, prekeys: Option[Seq[PreKey]] = None, lastKey: Option[PreKey] = None): ErrorOrResponse[Unit]
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

  private implicit val PreKeysResponseDeserializer: RawBodyDeserializer[PreKeysResponse] =
    RawBodyDeserializer[JSONObject].map(json => PreKeysResponse.unapply(JsonObjectResponse(json)).get)

  private implicit val ClientsDeserializer: RawBodyDeserializer[Seq[Client]] =
    RawBodyDeserializer[JSONArray].map(json => ClientsResponse.unapply(JsonArrayResponse(json)).get)

  //TODO We have to introduce basic deserializers for the seq
  private implicit val RemainingPreKeysDeserializer: RawBodyDeserializer[Seq[Int]] =
    RawBodyDeserializer[JSONArray].map(json => RemainingPreKeysResponse.unapply(JsonArrayResponse(json)).get)

  private implicit val ListClientsResponseDeserializer: RawBodyDeserializer[ListClientsResponse] =
    RawBodyDeserializer[JSONObject].map(ListClientsResponse.Decoder(_))

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

  override def loadClients(users: Set[QualifiedId]): ErrorOrResponse[Map[QualifiedId, Seq[Client]]] =
    Request.Post(relativePath = ListClientsPath, body = ListClientsRequest(users.toSeq).encode)
      .withResultType[ListClientsResponse]
      .withErrorType[ErrorResponse]
      .executeSafe(_.values)

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
      o.put("prekeys", JsonEncoder.arr(keys)(PreKeyEncoder))
      o.put("label", client.label)
      o.put("model", client.model)
      o.put("class", client.deviceClass.value)
      o.put("type", client.deviceType.getOrElse(DeviceType.Permanent).value)
      o.put("cookie", userId.str)

      password.map(_.str).foreach(o.put("password", _))
    }

    Request.Post(relativePath = clientsPath, body = data)
      .withResultType[Client]
      .withErrorType[ErrorResponse]
      .executeSafe(_.copy(verified = Verification.VERIFIED)) //TODO Maybe we can add description for this?
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

  override def updateKeys(id: ClientId, prekeys: Option[Seq[PreKey]] = None, lastKey: Option[PreKey] = None): ErrorOrResponse[Unit] = {
    val data = JsonEncoder { o =>
      lastKey.foreach(k => o.put("lastkey", JsonEncoder.encode(k)))
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
  val ListClientsPath = "/users/list-clients/v2"

  def clientPath(id: ClientId) = s"/clients/$id"
  def clientKeyIdsPath(id: ClientId) = s"/clients/$id/prekeys"
  def userPreKeysPath(user: UserId) = s"/users/$user/prekeys"
  def userClientsPath(user: UserId) = s"/users/$user/clients"
  def clientPreKeyPath(user: UserId, client: ClientId) = s"/users/$user/prekeys/$client"

  import JsonDecoder._

  type ClientKey = (ClientId, PreKey)

  private def userIdBytes(id: UserId): Array[Byte] =
    returning(Array.ofDim[Byte](16)) { bytes =>
      val bb = ByteBuffer.wrap(bytes).asLongBuffer()
      val uuid = UUID.fromString(id.str)
      bb.put(uuid.getMostSignificantBits)
      bb.put(uuid.getLeastSignificantBits)
    }

  def userId(id: UserId): Otr.UserId =
    Otr.UserId.newBuilder
      .setUuid(ByteString.copyFrom(userIdBytes(id)))
      .build

  def clientId(id: ClientId): Otr.ClientId =
    Otr.ClientId.newBuilder
      .setClient(id.longId)
      .build

  case class EncryptedContent(content: Map[UserId, Map[ClientId, Array[Byte]]]) {
    import scala.collection.JavaConverters._
    lazy val estimatedSize: Int = content.valuesIterator.map { cs => 16 + cs.valuesIterator.map(_.length + 8).sum }.sum

    lazy val userEntries: Array[Otr.UserEntry] = content.map { case (user, cs) =>
      val clients = cs.map { case (c, msg) =>
          Otr.ClientEntry.newBuilder
            .setClient(clientId(c))
            .setText(ByteString.copyFrom(msg))
            .build
      } (breakOut)
      Otr.UserEntry.newBuilder
        .setUser(userId(user))
        .addAllClients(clients.asJava)
        .build
    } (breakOut)
  }

  object EncryptedContent {
    val Empty: EncryptedContent = EncryptedContent(Map.empty)
  }

  lazy val EncryptedContentEncoder: JsonEncoder[EncryptedContent] = new JsonEncoder[EncryptedContent] {
    override def apply(content: EncryptedContent): JSONObject = JsonEncoder { o =>
      content.content.foreach { case (user, clients) =>
        o.put(user.str, JsonEncoder { u =>
          clients.foreach { case (c, msg) => u.put(c.str, AESUtils.base64(msg)) }
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

  final case class ListClientsRequest(qualifiedUsers: Seq[QualifiedId]) {
    def encode: JSONObject = JsonEncoder { o =>
      o.put(
        "qualified_users",
        JsonEncoder.array(qualifiedUsers) { case (arr, qid) => arr.put(QualifiedId.Encoder(qid)) }
      )
    }
  }

  object ListClientsRequest {
    implicit object Encoder extends JsonEncoder[ListClientsRequest] {
      override def apply(request: ListClientsRequest): JSONObject = request.encode
    }
  }

  final case class ListClientsResponse(values: Map[QualifiedId, Seq[Client]])

  object ListClientsResponse {
    val Empty: ListClientsResponse = ListClientsResponse(Map.empty)

    import scala.collection.JavaConverters._

    private def getClients(json: JSONArray): Seq[Client] =
      JsonDecoder.array(json, (arr, i) => Try(arr.getJSONObject(i)).map(Client.Decoder(_)).toOption).flatten

    private def getUserClients(domain: String, json: JSONObject): Map[QualifiedId, Seq[Client]] =
      json.keySet.asScala.toSeq.flatMap { userId =>
        Try(json.getJSONArray(userId)).map { clientsJson =>
          QualifiedId(UserId(userId), domain) -> getClients(clientsJson)
        }.toOption
      }.toMap

    implicit object Decoder extends JsonDecoder[ListClientsResponse] {
      override def apply(implicit js: JSONObject): ListClientsResponse = {
        val response = Try(js.getJSONObject("qualified_user_map")) match {
          case Success(jsMap) =>
            jsMap.keySet.asScala.toSeq.flatMap { domain =>
              Try(jsMap.getJSONObject(domain)).map(getUserClients(domain, _)).getOrElse(Map.empty)
            }.toMap
          case Failure(_) => Map.empty[QualifiedId, Seq[Client]]
        }
        if (response.nonEmpty) ListClientsResponse(response) else Empty
      }
    }
  }

  object ClientsResponse {

    def client(implicit js: JSONObject): Client =
      Client(
        id = decodeId[ClientId]('id),
        label = 'label,
        model = 'model,
        deviceClass = decodeOptString('class).fold(DeviceClass.Phone)(DeviceClass.apply),
        deviceType = decodeOptString('type).map(DeviceType.apply),
        regTime = decodeOptUtcDate('time).map(_.instant),
        regLocation = opt[Location]('location)
      )

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
    final case class Success(mismatch: ClientMismatch) extends MessageResponse
    final case class Failure(mismatch: ClientMismatch) extends MessageResponse
  }

  final case class ClientMismatch(redundant: Map[UserId, Seq[ClientId]] = Map.empty,
                                  missing:   Map[UserId, Seq[ClientId]] = Map.empty,
                                  deleted:   Map[UserId, Seq[ClientId]] = Map.empty,
                                  time:     RemoteInstant)

  object ClientMismatch {
    implicit lazy val Decoder: JsonDecoder[ClientMismatch] = new JsonDecoder[ClientMismatch] {
      import JsonDecoder._

      import scala.collection.JavaConverters._

      def decodeMap(key: Symbol)(implicit js: JSONObject): Map[UserId, Seq[ClientId]] =
        if (!js.has(key.name) || js.isNull(key.name)) Map.empty
        else {
          val mapJs = js.getJSONObject(key.name)
          mapJs.keys.asScala.map { key =>
            UserId(key) -> decodeStringSeq(Symbol(key))(mapJs).map(ClientId(_))
          }.toMap
        }

      override def apply(implicit js: JSONObject): ClientMismatch =
        ClientMismatch(decodeMap('redundant),
          decodeMap('missing),
          decodeMap('deleted),
          decodeOptUtcDate('time).map(t => RemoteInstant.ofEpochMilli(t.getTime)).getOrElse(RemoteInstant.Epoch))
    }
  }

}
