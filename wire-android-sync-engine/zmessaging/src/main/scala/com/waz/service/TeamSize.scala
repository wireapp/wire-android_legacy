/*
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.service

import com.waz.content._
import com.waz.model._
import com.waz.threading.Threading
import com.waz.utils.events.EventContext

import scala.concurrent.Future


trait TeamSize {
  def runIfNoThreshold(fnToRun: () => Future[_]): Future[Unit]
}

class TeamSizeImpl(teamId:       Option[TeamId],
                   usersStorage: UsersStorage) extends TeamSize {

  import Threading.Implicits.Background
  private implicit val ec: EventContext.Global.type = EventContext.Global

  private val teamSizeThreshold = 400

  override def runIfNoThreshold(fnToRun: () => Future[_]): Future[Unit] =
    selfTeamSize.flatMap {
        case Some(teamSize) if teamSize < teamSizeThreshold => fnToRun().map(_ => ())
        case _ => Future.successful({})
    }

  def selfTeamSize: Future[Option[Int]] = {
    val empty: Future[Option[Int]] = Future.successful(None)
    teamId.fold(empty)(id => usersStorage.getByTeam(Set(id)).map(users => Some(users.size)))
  }
}
