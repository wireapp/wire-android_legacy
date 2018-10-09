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
import com.waz.model.{TeamData, TeamId, UserId}
import com.waz.service.teams.TeamsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncResult
import com.waz.sync.client.TeamsClient
import com.waz.testutils.TestUserPreferences
import com.waz.threading.CancellableFuture

import scala.concurrent.Future


class TeamsSyncHandlerSpec extends AndroidFreeSpec {

  val client = mock[TeamsClient]
  val service = mock[TeamsService]
  val prefs   = new TestUserPreferences
  feature("Sync all teams") {

    scenario("Basic single team with some members sync") {

      val teamId = TeamId()
      val teams = Seq((teamId, true))
      val teamData = TeamData(teamId, "name", UserId())
      val members = Map(
        UserId() -> (0L, 0L),
        UserId() -> (0L, 0L)
      )

      (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
      (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Right(members)))
      (service.onTeamSynced _).expects(teamData, members).once().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult.Success

    }

    scenario("Failed members download should fail entire sync") {

      val teamId = TeamId()
      val teamData = TeamData(teamId, "name", UserId())

      val timeoutError = ErrorResponse(ErrorResponse.ConnectionErrorCode, s"Request failed with timeout", "connection-error")

      (client.getTeamData(_: TeamId)).expects(teamId).once().returning(CancellableFuture.successful(Right(teamData)))
      (client.getTeamMembers _).expects(teamId).once().returning(CancellableFuture.successful(Left(timeoutError)))

      (service.onTeamSynced _).expects(*, *).never().returning(Future.successful({}))

      result(initHandler(Some(teamId)).syncTeam()) shouldEqual SyncResult(timeoutError)
    }
  }

  def initHandler(teamId: Option[TeamId]) = new TeamsSyncHandlerImpl(account1Id, prefs, teamId, client, service)

}
