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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model._
import com.waz.service.{SearchKey, SearchQuery}
import com.waz.utils._
import com.wire.signals._

import scala.collection.breakOut
import scala.concurrent.Future

trait UsersStorage extends CachedStorage[UserId, UserData] {
  def getByTeam(team: Set[TeamId]): Future[Set[UserData]]
  def searchByTeam(team: TeamId, query: SearchQuery): Future[Set[UserData]]
  def listAll(ids: Traversable[UserId]): Future[Vector[UserData]]
  def listSignal(ids: Traversable[UserId]): Signal[Vector[UserData]]
  def listUsersByConnectionStatus(p: Set[ConnectionStatus]): Future[Map[UserId, UserData]]
  def listAcceptedOrPendingUsers: Future[Map[UserId, UserData]]

  def findUsersForService(id: IntegrationId): Future[Set[UserData]]

  def findByPicture(assetId: GeneralAssetId): Future[Option[UserData]]
}

final class UsersStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[UserId, UserData](
    new UnlimitedLruCache(), storage)(UserDataDao, LogTag("UsersStorage_Cached")
  ) with UsersStorage {
  import com.waz.threading.Threading.Implicits.Background

  override def listAll(ids: Traversable[UserId]): Future[Vector[UserData]] = getAll(ids).map(_.collect { case Some(x) => x }(breakOut))

  override def listSignal(ids: Traversable[UserId]): Signal[Vector[UserData]] = {
    val idSet = ids.toSet
    new RefreshingSignal(() => listAll(ids).lift, onChanged.map(_.filter(u => idSet(u.id))).filter(_.nonEmpty))
  }

  override def listUsersByConnectionStatus(p: Set[ConnectionStatus]): Future[Map[UserId, UserData]] =
    find[(UserId, UserData), Map[UserId, UserData]](
      user => p(user.connection) && !user.deleted,
      db   => UserDataDao.findByConnectionStatus(p)(db),
      user => (user.id, user))

  override def listAcceptedOrPendingUsers: Future[Map[UserId, UserData]] =
    find[(UserId, UserData), Map[UserId, UserData]](
      user => user.isAcceptedOrPending && !user.deleted,
      db   => UserDataDao.findByConnectionStatus(Set(ConnectionStatus.Accepted, ConnectionStatus.PendingFromOther, ConnectionStatus.PendingFromUser))(db),
      user => (user.id, user))

  override def getByTeam(teams: Set[TeamId]): Future[Set[UserData]] =
    find(data => data.teamId.exists(id => teams.contains(id)), UserDataDao.findForTeams(teams)(_), identity)

  override def findUsersForService(id: IntegrationId): Future[Set[UserData]] =
    find(_.integrationId.contains(id), UserDataDao.findService(id)(_), identity).map(_.toSet)

  override def searchByTeam(team: TeamId, query: SearchQuery): Future[Set[UserData]] =
    storage(UserDataDao.search(SearchKey(query.query), query.domain, query.handleOnly, Some(team))(_)).future

  override def findByPicture(assetId: GeneralAssetId): Future[Option[UserData]] =
    values.map(_.find(_.picture.exists(_.id == assetId)))
}
