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
import com.waz.model.UserPermissions.PermissionsMasks
import com.waz.model._
import com.waz.sync.client.TeamsClient.TeamMember
import com.waz.utils.{CirceJSONSupport, JsonDecoder}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}

import scala.util.Try

trait TeamsClient {
  def getTeamMembers(id: TeamId): ErrorOrResponse[Seq[TeamMember]]
  def getTeamData(id: TeamId): ErrorOrResponse[TeamData]
  def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[Option[PermissionsMasks]]
  def getTeamMember(teamId: TeamId, userId: UserId): ErrorOrResponse[TeamMember]
}

class TeamsClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends TeamsClient with CirceJSONSupport {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import TeamsClient._
  import com.waz.threading.Threading.Implicits.Background

  override def getTeamMembers(id: TeamId): ErrorOrResponse[Seq[TeamMember]] = {
    Request.Get(relativePath = teamMembersPath(id))
      .withResultType[TeamMembers]
      .withErrorType[ErrorResponse]
      .executeSafe(_.members)
  }

  override def getTeamData(id: TeamId): ErrorOrResponse[TeamData] = {
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

  override def getTeamMember(teamId: TeamId, userId: UserId): ErrorOrResponse[TeamMember] = {
    Request.Get(relativePath = memberPath(teamId, userId))
      .withResultType[TeamMember]
      .withErrorType[ErrorResponse]
      .executeSafe
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

  case class TeamMembers(members: Seq[TeamMember])

  case class TeamMember(user: UserId, permissions: Option[Permissions], created_by: Option[UserId])

  case class Permissions(self: Long, copy: Long)

}
