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
import com.waz.model.UserPermissions.PermissionsMasks
import com.waz.model._

import com.waz.sync.client.TeamsClient.{TeamMember, TeamMembers}
import com.waz.utils.{CirceJSONSupport, Json, JsonEncoder, _}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, Request}

trait TeamsClient {
  def getTeamMembers(id: TeamId): ErrorOrResponse[TeamMembers]
  def getTeamData(id: TeamId): ErrorOrResponse[TeamData]
  def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[Option[PermissionsMasks]]
  def getTeamMember(teamId: TeamId, userId: UserId): ErrorOrResponse[TeamMember]
  def deleteTeamConversation(teamId: TeamId, convId: RConvId): ErrorOrResponse[Unit]
  def getTeamRoles(id: TeamId): ErrorOrResponse[Set[ConversationRole]]
  def getTeamMembersWithPost(teamId: TeamId, userIds: Seq[UserId]): ErrorOrResponse[Seq[TeamMember]]
}

class TeamsClientImpl(implicit
                      urlCreator: UrlCreator,
                      httpClient: HttpClient,
                      authRequestInterceptor: AuthRequestInterceptor) extends TeamsClient with CirceJSONSupport {

  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import TeamsClient._
  import com.waz.threading.Threading.Implicits.Background

  override def getTeamMembers(id: TeamId): ErrorOrResponse[TeamMembers] = {
    Request.Get(relativePath = teamMembersPath(id))
      .withResultType[TeamMembers]
      .withErrorType[ErrorResponse]
      .executeSafe
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

  override def getTeamMembersWithPost(teamId: TeamId, userIds: Seq[UserId]): ErrorOrResponse[Seq[TeamMember]] = {
    import HttpClient.AutoDerivationOld.JsonBodySerializer
    Request.Post(
      relativePath = membersPostPath(teamId),
      body         = Json("user_ids" -> JsonEncoder.arrString(userIds.map(_.str)))
    ).withResultType[TeamMembers]
     .withErrorType[ErrorResponse]
     .executeSafe(_.members)
  }

  override def deleteTeamConversation(teamId: TeamId, convId: RConvId): ErrorOrResponse[Unit] = {
    Request.Delete(relativePath = teamConversationPath(teamId, convId))
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def getTeamRoles(id: TeamId): ErrorOrResponse[Set[ConversationRole]] =
    Request.Get(relativePath = teamRolesPath(id))
      .withResultType[TeamRoles]
      .withErrorType[ErrorResponse]
      .executeSafe(_.toConversationRoles)

  private def createPermissionsMasks(permissions: Permissions): PermissionsMasks =
    permissions.toMasks

}

object TeamsClient {

  val TeamsPath = "/teams"
  val TeamsPageSize = 100

  def teamMembersPath(id: TeamId) = s"$TeamsPath/${id.str}/members"

  def teamPath(id: TeamId): String = s"$TeamsPath/${id.str}"

  def teamRolesPath(id: TeamId): String = s"${teamPath(id)}/conversations/roles"

  def memberPath(teamId: TeamId, userId: UserId): String = s"${teamMembersPath(teamId)}/${userId.str}"

  def membersPostPath(teamId: TeamId): String = s"/teams/${teamId.str}/get-members-by-ids-using-post"

  def teamConversationPath(id: TeamId, cid: RConvId): String = s"$TeamsPath/${id.str}/conversations/${cid.str}"

  case class TeamMembers(members: Seq[TeamMember], hasMore: Boolean)

  case class TeamMember(user: UserId, permissions: Option[Permissions], created_by: Option[UserId]) {
    lazy val permissionMasks: PermissionsMasks = permissions.fold((0L, 0L))(_.toMasks)
  }

  case class Permissions(self: Long, copy: Long) {
    def toMasks: PermissionsMasks = (self, copy)
  }

  case class TeamConvRole(conversation_role: String, actions: Seq[String]) {
    def toConversationRole: ConversationRole = ConversationRole(conversation_role, actions.flatMap(a => ConversationAction.allActions.find(_.name == a)).toSet)
  }

  case class TeamRoles(conversation_roles: Seq[TeamConvRole]) {
    def toConversationRoles: Set[ConversationRole] = conversation_roles.map(_.toConversationRole).toSet
  }

}
