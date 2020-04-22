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
package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.model.{AssetId, ConversationRole, TeamData, TeamId, UserId}
import com.waz.service.teams.TeamsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.TeamsClient
import com.waz.sync.client.TeamsClient.{Permissions, TeamMember, TeamMembers}
import com.waz.testutils.TestUserPreferences
import com.waz.threading.CancellableFuture

import scala.concurrent.Future

class TeamsSyncHandlerSpec extends AndroidFreeSpec {

  val client  = mock[TeamsClient]
  val service = mock[TeamsService]
  val prefs   = new TestUserPreferences

  feature("Sync all teams") {

    scenario("Basic single team with members less than 1500 sync") {

      val teamId = TeamId()
      val teamData = TeamData(teamId, "name", UserId(), AssetId())
      val members = Seq(
        TeamMember(UserId(), Option(Permissions(0L, 0L)), None),
        TeamMember(UserId(), Option(Permissions(0L, 0L)), None)
      )
      val teamMembersData = TeamMembers(members, has_more = false)

      (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
      (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Right(teamMembersData)))
      (client.getTeamRoles _).expects(teamId).once().returning(CancellableFuture.successful(Right(ConversationRole.defaultRoles)))
      (service.onTeamSynced _).expects(teamData, members, ConversationRole.defaultRoles).once().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult.Success

    }

    scenario("Basic single team with members over 2000 sync") {

      val teamId = TeamId()
      val teamData = TeamData(teamId, "name", UserId(), AssetId())
      val members = Seq(
        TeamMember(UserId(), Option(Permissions(0L, 0L)), None),
        TeamMember(UserId(), Option(Permissions(0L, 0L)), None)
      )
      val teamMembersData = TeamMembers(members, has_more = true)

      (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
      (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Right(teamMembersData)))
      (client.getTeamRoles _).expects(teamId).once().returning(CancellableFuture.successful(Right(ConversationRole.defaultRoles)))
      (service.onTeamSynced _).expects(teamData, Seq.empty[TeamMember], ConversationRole.defaultRoles).once().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult.Success

    }

    scenario("Failed members download should fail entire sync") {

      val teamId = TeamId()
      val teamData = TeamData(teamId, "name", UserId(), AssetId())

      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
      (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Left(timeoutError)))

      (service.onTeamSynced _).expects(*, *, *).never().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult(timeoutError)
    }
  }

  feature("Sync a single team member") {

    scenario("Team member sync succeeds") {
      val teamId = TeamId()
      val userId = UserId()
      val teamMember = TeamMember(userId, Option(Permissions(0L, 0L)), None)
      (client.getTeamMember(_: TeamId, _:UserId)).expects(teamId, userId).once().returning(CancellableFuture.successful(Right(teamMember)))
      (service.onMemberSynced _).expects(teamMember).once().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncMember(userId)) shouldEqual SyncResult.Success
    }

    scenario("Team member sync fails") {
      val teamId = TeamId()
      val userId = UserId()
      val teamMember = TeamMember(userId, Option(Permissions(0L, 0L)), None)
      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      (client.getTeamMember(_: TeamId, _:UserId)).expects(teamId, userId).once().returning(CancellableFuture.successful(Left(timeoutError)))
      (service.onMemberSynced _).expects(teamMember).never().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncMember(userId)) shouldEqual SyncResult(timeoutError)
    }
  }

  def initHandler(teamId: Option[TeamId]) = new TeamsSyncHandlerImpl(account1Id, prefs, teamId, client, service)

}
