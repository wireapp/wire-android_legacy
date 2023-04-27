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

import com.waz.log.LogSE._
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.sync.client.ConnectionsClient.PageSize
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.JsonDecoder._
import com.waz.utils.{Json, _}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.HttpClient.HttpClientError
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

import scala.util.Try
import scala.util.control.NonFatal

trait ConnectionsClient {
  def loadConnections(start: Option[UserId] = None, pageSize: Int = PageSize): ErrorOrResponse[Seq[UserConnectionEvent]]
  def loadConnection(id: UserId): ErrorOrResponse[UserConnectionEvent]
  def createConnection(user: UserId, name: Name, message: String): ErrorOrResponse[UserConnectionEvent]
  def createConnection(qId: QualifiedId): ErrorOrResponse[UserConnectionEvent]
  def updateConnection(user: UserId, status: ConnectionStatus): ErrorOrResponse[Option[UserConnectionEvent]]
}

class ConnectionsClientImpl(implicit
                            urlCreator: UrlCreator,
                            httpClient: HttpClient,
                            authRequestInterceptor: AuthRequestInterceptor) extends ConnectionsClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import Threading.Implicits.Background
  import com.waz.sync.client.ConnectionsClient._

  private implicit val UserConnectionEventDeserializer: RawBodyDeserializer[UserConnectionEvent] =
    RawBodyDeserializer[JSONObject].map(json => ConnectionResponseExtractor.unapply(JsonObjectResponse(json)).get)

  private implicit val UserConnectionsEventDeserializer: RawBodyDeserializer[(Seq[UserConnectionEvent], Boolean)] =
    RawBodyDeserializer[JSONObject].map(json => ConnectionsResponseExtractor.unapply(JsonObjectResponse(json)).get)

  override def loadConnections(start: Option[UserId] = None, pageSize: Int = PageSize): ErrorOrResponse[Seq[UserConnectionEvent]] = {
    Request
      .Get(
        relativePath = ConnectionsPath,
        queryParameters = ("size" -> pageSize.toString) :: start.fold2(List.empty, s => List("start" -> s.str))
      )
      .withResultType[(Seq[UserConnectionEvent], Boolean)]()
      .withErrorType[ErrorResponse]
      .execute
      .flatMap { case (events, hasMore) =>
        if (hasMore) loadConnections(Some(events.last.to), pageSize).map { _.right.map(events ++ _) }
        else CancellableFuture.successful(Right(events))
      }
      .recover {
        case err: ErrorResponse => Left(err)
        case err: HttpClientError => Left(ErrorResponse.errorResponseConstructor.constructFrom(err))
      }
  }

  override def loadConnection(id: UserId): ErrorOrResponse[UserConnectionEvent] = {
    Request.Get(relativePath = s"$ConnectionsPath/$id")
      .withResultType[UserConnectionEvent]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def createConnection(user: UserId, name: Name, message: String): ErrorOrResponse[UserConnectionEvent] = {
    val jsonData = Json("user" -> user.toString, "name" -> name, "message" -> message)
    Request.Post(relativePath = ConnectionsPath, body = jsonData)
      .withResultType[UserConnectionEvent]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def createConnection(qId: QualifiedId): ErrorOrResponse[UserConnectionEvent] =
    Request.Post(relativePath = qualifiedConnectionsPath(qId), body = "")
      .withResultType[UserConnectionEvent]()
      .withErrorType[ErrorResponse]
      .executeSafe

  override def updateConnection(user: UserId, status: ConnectionStatus): ErrorOrResponse[Option[UserConnectionEvent]] = {
    val jsonData = Json("status" -> status.code)
    Request.Put(relativePath = s"$ConnectionsPath/${user.str}", body = jsonData)
      .withResultType[Option[UserConnectionEvent]]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }
}

object ConnectionsClient extends DerivedLogTag {
  val ConnectionsPath = "/connections"
  val PageSize = 100

  def qualifiedConnectionsPath(qId: QualifiedId): String =
    s"$ConnectionsPath/${qId.domain}/${qId.id}"

  object ConnectionResponseExtractor extends DerivedLogTag {
    def unapply(resp: ResponseContent): Option[UserConnectionEvent] = resp match {
      case JsonObjectResponse(js) => Try(UserConnectionEvent.Decoder(js)).toOption
      case _ =>
        warn(l"unknown response format for connection response:")
        None
    }
  }

  object ConnectionsResponseExtractor {
    def unapply(response: ResponseContent): Option[(List[UserConnectionEvent], Boolean)] = try {
      response match {
        case JsonObjectResponse(js) =>
          Some((if (js.has("connections")) array[UserConnectionEvent](js.getJSONArray("connections")).toList else Nil, decodeBool('has_more)(js)))
        case _ =>
          warn(l"unknown response format for connections response:")
          None
      }
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse connections response:", e)
        None
    }
  }
}
