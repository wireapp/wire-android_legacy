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
import com.wire.signals.CancellableFuture

import scala.concurrent.Future

class TeamsSyncHandlerSpec extends AndroidFreeSpec {

  val client  = mock[TeamsClient]
  val service = mock[TeamsService]
  val prefs   = new TestUserPreferences

  feature("Sync all teams") {

    scenario("Basic single team with members that has less than 1500 sync") {
      verifyTeamSync(has_more = false)
    }

    scenario("Basic single team with members that has more than 1500 sync") {
      verifyTeamSync(has_more = true)

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

    scenario("Retrieve team members from the backend") {
      val teamId = TeamId()
      val handler = initHandler(Some(teamId))
      val teamMembers =
        Seq(UserId(), UserId())
          .map(userId => userId -> TeamMember(userId, Option(Permissions(0L, 0L)), None))
          .toMap
      (client.getTeamMembersWithPost _)
        .expects(teamId, *)
        .anyNumberOfTimes()
        .onCall { (_: TeamId, userIds: Seq[UserId]) =>
          CancellableFuture.successful(Right(teamMembers.filterKeys(userIds.contains).values.toSeq))
        }

      result(handler.getMembers(teamMembers.keys.toSeq)).sortBy(_.user) shouldEqual teamMembers.values.toSeq.sortBy(_.user)
    }
  }

  private def verifyTeamSync(has_more: Boolean) = {
    val teamId = TeamId()
    val teamData = TeamData(teamId, "name", UserId(), AssetId())
    val members = Seq(
      TeamMember(UserId(), Option(Permissions(0L, 0L)), None),
      TeamMember(UserId(), Option(Permissions(0L, 0L)), None)
    )
    val teamMembersData = TeamMembers(members, has_more)

    (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
    (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Right(teamMembersData)))
    (client.getTeamRoles _).expects(teamId).once().returning(CancellableFuture.successful(Right(ConversationRole.defaultRoles)))
    (service.onTeamSynced _).expects(teamData, if (has_more) Seq.empty[TeamMember] else members, ConversationRole.defaultRoles).once().returning(Future.successful({}))

    result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult.Success
  }

  def initHandler(teamId: Option[TeamId]) = new TeamsSyncHandlerImpl(account1Id, prefs, teamId, client, service)

}
