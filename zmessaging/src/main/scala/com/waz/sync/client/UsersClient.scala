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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.tracking.TrackingService.NoReporting
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet.ContentEncoder.JsonContentEncoder
import com.waz.znet.Response.Status.Success
import com.waz.znet.Response.{ErrorStatus, HttpStatus, SuccessHttpStatus}
import com.waz.znet.ZNetClient.{ErrorOr, ErrorOrResponse}
import com.waz.znet.{JsonArrayResponse, JsonObjectResponse, _}
import org.json.JSONObject

import scala.concurrent.Future
import scala.util.{Right, Try}

class UsersClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.UsersClient._

  def loadUsers(ids: Seq[UserId]): ErrorOrResponse[IndexedSeq[UserInfo]] =
    if (ids.isEmpty) CancellableFuture.successful(Right(Vector())) else {
      CancellableFuture.lift(Future.traverse(ids.grouped(IdsCountThreshold).toSeq) { ids => // split up every IdsCountThreshold user ids so that the request uri remains short enough (ca. 2400 bytes for 64 ids)
        netClient(Request.Get(usersPath(ids))) map {
          case Response(SuccessHttpStatus(), UserResponseExtractor(users@_*), _) => users
          case Response(status, ErrorResponse(code, msg, label), _) =>
            warn(s"error response to loadUsers query: ${ErrorResponse(code, msg, label)}")
            throw new FailedLoadUsersResponse(ErrorResponse(code, msg, label))
          case Response(status @ ErrorStatus(), _, _) =>
            warn(s"unexpected response to loadUsers query: $status")
            throw new FailedLoadUsersResponse(ErrorResponse(status.status, status.msg, ""))
        }
      } .map (_.flatten.toIndexedSeq) .map (Right(_)) .recover { case e: FailedLoadUsersResponse => Left(e.error) }, {})
    }

  def loadSelf(): ErrorOrResponse[UserInfo] =
    netClient.withErrorHandling("loadSelf", Request.Get(SelfPath)) {
      case Response(SuccessHttpStatus(), UserResponseExtractor(user), _) => user
    }

  def updateSelf(info: UserInfo): ErrorOrResponse[Unit] = {
    debug(s"updateSelf: $info, picture: ${info.picture}")
    netClient.updateWithErrorHandling("updateSelf", Request.Put(SelfPath, info))
  }

  def deleteAccount(password: Option[String] = None): ErrorOr[Unit] = netClient.withFutureErrorHandling("delete account", Request.Delete(SelfPath, data = Some(DeleteAccount(password)))) {
    case Response(SuccessHttpStatus(), resp, _) => Right(())
  }

  def setSearchable(searchable: Boolean): ErrorOrResponse[Unit] = {
    val req = Request.Put(SearchablePath, JsonContentEncoder(JsonEncoder(_.put("searchable", searchable))))
    netClient.withErrorHandling("setSearchable", req) {
      case Response(HttpStatus(Success, _), _, _) =>
        debug(s"Searchable status updated: $searchable")
        Right(())
    }
  }

}

object UsersClient {
  val UsersPath = "/users"
  val SelfPath = "/self"
  val ConnectionsPath = "/self/connections"
  val SearchablePath = "/self/searchable"
  val IdsCountThreshold = 64

  def usersPath(ids: Seq[UserId]) = Request.query(UsersPath, "ids" -> ids.mkString(","))

  object UserResponseExtractor {
    def unapplySeq(resp: ResponseContent): Option[Seq[UserInfo]] = resp match {
      case JsonArrayResponse(js) => Try(JsonDecoder.array[UserInfo](js)).toOption
      case JsonObjectResponse(js) => Try(Vector(UserInfo.Decoder(js))).toOption
      case _ => None
    }
  }

  case class DeleteAccount(password: Option[String])

  implicit lazy val DeleteAccountEncoder: JsonEncoder[DeleteAccount] = new JsonEncoder[DeleteAccount] {
    override def apply(v: DeleteAccount): JSONObject = JsonEncoder { o =>
      v.password foreach (o.put("password", _))
    }
  }

  class FailedLoadUsersResponse(val error: ErrorResponse) extends RuntimeException(s"loading users failed with: $error") with NoReporting

}
