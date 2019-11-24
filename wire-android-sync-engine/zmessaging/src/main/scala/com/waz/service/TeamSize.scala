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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.log.LogSE._

import scala.concurrent.Future


trait TeamSize {
  def runIfNoThreshold(fnToRun: () => Future[_]): Future[Unit]
  def membersCount: Future[Option[Int]]
}

class TeamSizeImpl(teamId:       Option[TeamId],
                   usersStorage: UsersStorage) extends TeamSize {

  import Threading.Implicits.Background
  private implicit val ec: EventContext.Global.type = EventContext.Global

  override def runIfNoThreshold(fnToRun: () => Future[_]): Future[Unit] =
    membersCount.flatMap {
        case Some(membersCount) if membersCount < TeamSize.teamSizeThreshold => fnToRun().map(_ => ())
        case _ => Future.successful({})
    }

  override def membersCount: Future[Option[Int]] = {
    val empty: Future[Option[Int]] = Future.successful(None)
    teamId.fold(empty)(id => usersStorage.getByTeam(Set(id)).map(users => Some(users.size)))
  }
}

object TeamSize extends DerivedLogTag {

  val teamSizeThreshold = 400

  def hideStatus(teamId: Signal[Option[TeamId]], usersStorage: Signal[UsersStorage]): Signal[Boolean] =
    for {
      teamId <- teamId
      usersStorage <- usersStorage
      teamSize <- teamId.fold(Signal.const(0))(tId => Signal.future(usersStorage.getByTeam(Set(tId)).map(_.size)(Threading.Background)))
      hiding = teamSize == 0 || teamSize > teamSizeThreshold
      _ = verbose(l"Team size is ${teamSize} vs. threshold ${teamSizeThreshold}, hiding? ${hiding}")
    } yield hiding
}
