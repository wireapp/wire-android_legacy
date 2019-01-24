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
import com.waz.ZLog.warn
import com.waz.api.impl.ErrorResponse
import com.waz.model.AccountDataOld.PermissionsMasks
import com.waz.model._
import com.waz.utils.{CirceJSONSupport, JsonDecoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}

import scala.util.Try

trait TeamsClient {
  def getTeamMembers(id: TeamId): ErrorOrResponse[Map[UserId, Option[PermissionsMasks]]]
  def getTeamData(id: TeamId): ErrorOrResponse[TeamData]

  def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[Option[PermissionsMasks]]
}

class TeamsClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends TeamsClient with CirceJSONSupport {

  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import TeamsClient._
  import com.waz.threading.Threading.Implicits.Background

  //TODO Remove after assets refactoring
  private implicit val errorResponseDeserializer: RawBodyDeserializer[ErrorResponse] =
    objectFromCirceJsonRawBodyDeserializer[ErrorResponse]

  override def getTeamMembers(id: TeamId): ErrorOrResponse[Map[UserId, Option[PermissionsMasks]]] = {
    Request.Get(relativePath = teamMembersPath(id))
      .withResultType[TeamMembers]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        response.members
          .map(member => member.user -> member.permissions.map(createPermissionsMasks))
          .toMap
      }
  }

  override def getTeamData(id: TeamId): ErrorOrResponse[TeamData] = {
    implicit val deserializer: RawBodyDeserializer[TeamData] = teamDataDeserializer

    Request.Get(relativePath = teamPath(id))
      .withResultType[TeamData]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[Option[PermissionsMasks]] = {
    Request.Get(relativePath = memberPath(teamId, userId))
      .withResultType[TeamMember]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        response.permissions.map(createPermissionsMasks)
      }
  }

  private def createPermissionsMasks(permissions: Permissions): PermissionsMasks =
    (permissions.self, permissions.copy)

}

object TeamsClient {

  val TeamsPath = "/teams"
  val TeamsPageSize = 100

  def teamMembersPath(id: TeamId) = s"$TeamsPath/${id.str}/members"

  def teamPath(id: TeamId): String = s"$TeamsPath/${id.str}"

  def memberPath(teamId: TeamId, userId: UserId): String = s"${teamMembersPath(teamId)}/${userId.str}"

  import JsonDecoder._

  case class TeamBindingResponse(teams: Seq[(TeamData, Boolean)], hasMore: Boolean)

  //TODO Remove after assets refactoring
  object TeamBindingResponse {
    def unapply(response: ResponseContent): Option[(Seq[(TeamData, Boolean)], Boolean)] =
      response match {
        case JsonObjectResponse(js) if js.has("teams") =>
          Try(decodeSeq('teams)(js, TeamData.TeamBindingDecoder), decodeOptBoolean('has_more)(js).getOrElse(false)).toOption
        case _ =>
          warn(s"Unexpected response: $response")
          None
      }
  }

  case class TeamMembers(members: Seq[TeamMember])

  case class TeamMember(user: UserId, permissions: Option[Permissions])

  case class Permissions(self: Long, copy: Long)

  //TODO Remove after assets refactoring
  val teamDataDeserializer: RawBodyDeserializer[TeamData] = {
    import HttpClient.AutoDerivation._
    RawBodyDeserializer[(TeamData, Boolean)].map(_._1)
  }

}
