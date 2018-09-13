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
import com.waz.utils.JsonDecoder
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RawBodyDeserializer, Request}
import org.json.JSONObject

import scala.util.Try

trait TeamsClient {
  def getTeamMembers(id: TeamId): ErrorOrResponse[Map[UserId, PermissionsMasks]]
  def getTeamData(id: TeamId): ErrorOrResponse[TeamData]

  def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[PermissionsMasks]
}

class TeamsClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends TeamsClient {

  import HttpClient.dsl._
  import HttpClient.AutoDerivation._
  import TeamsClient._

  private implicit val teamMembersDeserializer: RawBodyDeserializer[Map[UserId, PermissionsMasks]] =
    RawBodyDeserializer[JSONObject].map(json => TeamMembersResponse.unapply(JsonObjectResponse(json)).get)

  private implicit val teamDataDeserializer: RawBodyDeserializer[TeamData] = {
    import TeamData.TeamBindingDecoder
    RawBodyDeserializer[(TeamData, Boolean)].map(_._1)
  }

  private implicit val permissionsMasksDeserializer: RawBodyDeserializer[PermissionsMasks] =
    RawBodyDeserializer[(UserId, PermissionsMasks)].map(_._2)

  override def getTeamMembers(id: TeamId): ErrorOrResponse[Map[UserId, PermissionsMasks]] = {
    Request.Get(relativePath = teamMembersPath(id))
      .withResultType[Map[UserId, PermissionsMasks]]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getTeamData(id: TeamId): ErrorOrResponse[TeamData] = {
    Request.Get(relativePath = teamPath(id))
      .withResultType[TeamData]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[PermissionsMasks] = {
    Request.Get(relativePath = memberPath(teamId, userId))
      .withResultType[PermissionsMasks]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

}

object TeamsClient {

  val TeamsPath = "/teams"
  val TeamsPageSize = 100

  def teamMembersPath(id: TeamId) = s"$TeamsPath/${id.str}/members"

  def teamPath(id: TeamId): String = s"$TeamsPath/${id.str}"

  def memberPath(teamId: TeamId, userId: UserId): String = s"${teamMembersPath(teamId)}/${userId.str}"

  import JsonDecoder._

  case class TeamBindingResponse(teams: Seq[(TeamData, Boolean)], hasMore: Boolean)

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

  implicit lazy val TeamMemberDecoder: JsonDecoder[(UserId, PermissionsMasks)] = JsonDecoder.lift { implicit js =>
    (decodeId[UserId]('user), (decodeInt('self)('permissions), decodeInt('copy)('permissions)))
  }

  object TeamMembersResponse {
    def unapply(response: ResponseContent): Option[Map[UserId, PermissionsMasks]] = {
      response match {
        case JsonObjectResponse(js) if js.has("members") =>
          Try(decodeSet('members)(js, TeamMemberDecoder)).toOption.map(_.toMap)
        case _ =>
          warn(s"Unexpected response: $response")
          None
      }
    }
  }

}
