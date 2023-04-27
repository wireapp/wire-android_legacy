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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.wire.signals.CancellableFuture
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http._
import org.json.{JSONArray, JSONObject}

import scala.concurrent.Future
import scala.util.Right

trait UsersClient {
  def loadUser(id: UserId): ErrorOrResponse[Option[UserInfo]]
  def loadUsers(ids: Seq[UserId]): ErrorOrResponse[Seq[UserInfo]]

  def loadQualifiedUser(qId: QualifiedId): ErrorOrResponse[Option[UserInfo]]
  def loadQualifiedUsers(qIds: Set[QualifiedId]): ErrorOrResponse[Seq[UserInfo]]

  def loadSelf(): ErrorOrResponse[UserInfo]
  def loadRichInfo(user: UserId): ErrorOrResponse[Seq[UserField]]
  def updateSelf(info: UserInfo): ErrorOrResponse[Unit]
  def deleteAccount(password: Option[String] = None): ErrorOr[Unit]
  def setSearchable(searchable: Boolean): ErrorOrResponse[Unit]
}

class UsersClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends UsersClient with DerivedLogTag {

  import HttpClient.dsl._
  import HttpClient.AutoDerivationOld._
  import Threading.Implicits.Background
  import com.waz.sync.client.UsersClient._

  private implicit val UsersResponseDeserializer: RawBodyDeserializer[Seq[UserInfo]] =
    RawBodyDeserializer[JSONArray].map(json => JsonDecoder.array[UserInfo](json))

  override def loadUser(id: UserId): ErrorOrResponse[Option[UserInfo]] =
    Request.Get(relativePath = usersPath(id))
      .withResultType[UserInfo]()
      .withErrorType[ErrorResponse]
      .execute
      .map {
        case res if res.deleted => Right(None)
        case res => Right(Some(res))
      }
      .recover {
        case e: ErrorResponse if e.code == ErrorResponse.NotFound => Right(None)
        case e: ErrorResponse => Left(e)
      }

  override def loadQualifiedUser(qId: QualifiedId): ErrorOrResponse[Option[UserInfo]] =
    Request.Get(relativePath = qualifiedPath(qId))
      .withResultType[UserInfo]()
      .withErrorType[ErrorResponse]
      .execute
      .map {
        case res if res.deleted => Right(None)
        case res => Right(Some(res))
      }
      .recover {
        case e: ErrorResponse if e.code == ErrorResponse.NotFound => Right(None)
        case e: ErrorResponse => Left(e)
      }

  override def loadUsers(ids: Seq[UserId]): ErrorOrResponse[Seq[UserInfo]] = {
    if (ids.isEmpty) CancellableFuture.successful(Right(Vector()))
    else {
      val result = Future.traverse(ids.grouped(IdsCountThreshold).toSeq) { ids => // split up every IdsCountThreshold user ids so that the request uri remains short enough
        Request.Get(relativePath = UsersPath, queryParameters = queryParameters("ids" -> ids.mkString(",")))
          .withResultType[Seq[UserInfo]]()
          .withErrorType[ErrorResponse]
          .execute
      }.map(_.flatten).map(Right(_)).recover { case e: ErrorResponse => Left(e) }

      CancellableFuture.lift(result)
    }
  }

  override def loadQualifiedUsers(qIds: Set[QualifiedId]): ErrorOrResponse[Seq[UserInfo]] =
    if (qIds.isEmpty)
      CancellableFuture.successful(Right(Vector()))
    else
      Request.Post(relativePath = ListUsersPath, body = ListUsersRequest(qIds).encode)
        .withResultType[Seq[UserInfo]]()
        .withErrorType[ErrorResponse]
        .executeSafe

  override def loadSelf(): ErrorOrResponse[UserInfo] = {
    Request.Get(relativePath = SelfPath)
      .withResultType[UserInfo]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadRichInfo(user: UserId): ErrorOrResponse[Seq[UserField]] = {
    Request.Get(relativePath = RichInfoPath(user))
      .withResultType[Seq[UserField]]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def updateSelf(info: UserInfo): ErrorOrResponse[Unit] = {
    debug(l"updateSelf: $info, picture: ${info.picture}")
    Request.Put(relativePath = SelfPath, body = info)
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def deleteAccount(password: Option[String] = None): ErrorOr[Unit] = {
    Request.Delete(relativePath = SelfPath, body = DeleteAccount(password))
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
      .future
  }

  override def setSearchable(searchable: Boolean): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = SearchablePath, body = JsonEncoder(_.put("searchable", searchable)))
      .withResultType[Unit]()
      .withErrorType[ErrorResponse]
      .executeSafe
  }

}

object UsersClient {
  val UsersPath = "/users"
  val ListUsersPath = "/list-users"
  val SelfPath = "/self"
  def RichInfoPath(user: UserId) = s"$UsersPath/${user.str}/rich-info"
  val ConnectionsPath = "/self/connections"
  val SearchablePath = "/self/searchable"
  val IdsCountThreshold = 64

  def usersPath(user: UserId): String = s"$UsersPath/${user.str}"
  def qualifiedPath(qId: QualifiedId): String = s"$UsersPath/${qId.domain}/${qId.id}"

  case class DeleteAccount(password: Option[String])

  implicit lazy val DeleteAccountEncoder: JsonEncoder[DeleteAccount] = new JsonEncoder[DeleteAccount] {
    override def apply(v: DeleteAccount): JSONObject = JsonEncoder { o =>
      v.password foreach (o.put("password", _))
    }
  }

  final case class ListUsersRequest(qIds: Set[QualifiedId]) {
    def encode: JSONObject = JsonEncoder {
      _.put("qualified_ids", QualifiedId.encode(qIds))
    }
  }

  object ListClientsRequest {
    implicit object Encoder extends JsonEncoder[ListUsersRequest] {
      override def apply(request: ListUsersRequest): JSONObject = request.encode
    }
  }
}
