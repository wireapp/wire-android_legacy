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

import scala.concurrent.Future


trait TeamSizeThreshold {
  def runIfBelowStatusPropagationThreshold(fnToRun: () => Future[_]): Future[Unit]
}

class TeamSizeThresholdImpl(teamId:       Option[TeamId],
                            usersStorage: UsersStorage) extends TeamSizeThreshold {

  import Threading.Implicits.Background
  private implicit val ec: EventContext.Global.type = EventContext.Global

  override def runIfBelowStatusPropagationThreshold(fnToRun: () => Future[_]): Future[Unit] =
    TeamSizeThreshold.isAboveStatusPropagationThreshold(teamId, usersStorage).map {
      case false => fnToRun().map(_ => ())
      case _ => Future.successful({})
    }
}

object TeamSizeThreshold extends DerivedLogTag {

  val statusPropagationThreshold = 400
  import Threading.Implicits.Background

  private def membersCount(teamId: Option[TeamId], usersStorage: UsersStorage): Future[Option[Int]] = {
    val empty: Future[Option[Int]] = Future.successful(None)
    teamId.fold(empty)({ id => usersStorage.getByTeam(Set(id)).map(users => Some(users.filter(!_.deleted).size))})
  }

  def isAboveStatusPropagationThreshold(teamId: Option[TeamId], usersStorage: UsersStorage): Future[Boolean] =
    membersCount(teamId, usersStorage).map { maybeSize =>
      maybeSize.exists(_ >= statusPropagationThreshold)
    }

  def shouldHideStatus(teamId: Signal[Option[TeamId]], usersStorage: Signal[UsersStorage]): Future[Boolean] =
    for {
      teamId        <- teamId.head
      usersStorage  <- usersStorage.head
      hiding        <- teamId.fold(Future.successful(true))(id => isAboveStatusPropagationThreshold(Some(id), usersStorage))
    } yield hiding
}
