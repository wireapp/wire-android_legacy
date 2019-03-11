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
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.{CopyPermissions, SelfPermissions}
import com.waz.model._
import com.waz.service.teams.TeamsService
import com.waz.sync.SyncResult
import com.waz.sync.client.TeamsClient
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.Right
import scala.util.control.NoStackTrace

trait TeamsSyncHandler {
  def syncTeam(): Future[SyncResult]
  def syncMember(id: UserId): Future[SyncResult]
  def syncSelfPermissions(): Future[SyncResult]
}

class TeamsSyncHandlerImpl(userId:    UserId,
                           userPrefs: UserPreferences,
                           teamId:    Option[TeamId],
                           client:    TeamsClient,
                           service:   TeamsService) extends TeamsSyncHandler {

  import Threading.Implicits.Background

  override def syncTeam(): Future[SyncResult] = teamId match {
    case Some(id) => client.getTeamData(id).future.flatMap {
      case Right(data) => client.getTeamMembers(id).future.flatMap {
        case Right(members) =>
          service
            .onTeamSynced(data, members)
            .map(_ => SyncResult.Success)
        case Left(errorResponse) =>
          Future.successful(SyncResult(errorResponse))
      }
      case Left(error) => Future.successful(SyncResult(error))
    }
    case None => Future.successful(SyncResult.Success)
  }

  override def syncMember(uId: UserId) = teamId match {
    case Some(tId) =>
      client.getTeamMember(tId, uId).future.flatMap {
        case Right(member) =>
          service
            .onMemberSynced(member)
            .map(_ => SyncResult.Success)
        case Left(e) =>
          Future.successful(SyncResult(e))
      }
    case _ => Future.successful(SyncResult.Success)
  }

  override def syncSelfPermissions() =
    teamId match {
      case Some(t) =>
        client.getPermissions(t, userId).future.flatMap {
          case Right(Some((self, copy))) =>
            for {
              _ <- userPrefs(SelfPermissions) := self
              _ <- userPrefs(CopyPermissions) := copy
            } yield SyncResult.Success
          case Right(None) =>
            //it should never happen
            //TODO Replace with custom exception after assets refactoring
            Future.successful(SyncResult(new RuntimeException(s"Permissions for $userId not found.")))
          case Left(err) =>
            Future.successful(SyncResult(err))
        }
      case None => Future.successful(SyncResult.Success) // no team - nothing to do
    }
}

object TeamsSyncHandler {
  case class SyncException(msg: String, err: ErrorResponse) extends Exception(msg) with NoStackTrace
}
