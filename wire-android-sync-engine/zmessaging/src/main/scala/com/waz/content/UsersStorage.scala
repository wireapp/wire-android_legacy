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
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.{breakOut, mutable}
import scala.concurrent.Future

trait UsersStorage extends CachedStorage[UserId, UserData] {
  def getByTeam(team: Set[TeamId]): Future[Set[UserData]]
  def searchByTeam(team: TeamId, prefix: SearchKey, handleOnly: Boolean): Future[Set[UserData]]
  def listAll(ids: Traversable[UserId]): Future[Vector[UserData]]
  def listSignal(ids: Traversable[UserId]): Signal[Vector[UserData]]
  def listUsersByConnectionStatus(p: Set[ConnectionStatus]): Future[Map[UserId, UserData]]
  def listAcceptedOrPendingUsers: Future[Map[UserId, UserData]]
  def getOrElseUpdate(id: UserId, default: => UserData): Future[UserData]
  def addOrOverwrite(user: UserData): Future[UserData]

  def findUsersForService(id: IntegrationId): Future[Set[UserData]]
}

class UsersStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[UserId, UserData](new TrimmingLruCache(context, Fixed(2000)), storage)(UserDataDao, LogTag("UsersStorage_Cached"))
    with UsersStorage {

  import EventContext.Implicits.global
  private implicit val dispatcher = new SerialDispatchQueue(name = "UsersStorage")

  val contactsByName = new mutable.HashMap[String, mutable.Set[UserId]] with mutable.MultiMap[String, UserId]

  private lazy val contactNamesSource = Signal[Map[UserId, (NameParts, SearchKey)]]()

  def contactNames: Signal[Map[UserId, (NameParts, SearchKey)]] = contactNamesSource

  private lazy val contactNameParts: CancellableFuture[mutable.HashMap[UserId, NameParts]] = storage { UserDataDao.listContacts(_) } map { us =>
    val cs = new mutable.HashMap[UserId, NameParts]
    us foreach { user =>
      if (getRawCached(user.id) == null) put(user.id, user)
      updateContactName(user, cs)
    }
    cs
  }

  onAdded { users =>
    users foreach { user =>
      // TODO: batch
      if (user.isConnected || user.connection == ConnectionStatus.Self) updateContactName(user)
    }
  }

  onUpdated { updates =>
    updates foreach { case (user, updated) =>
      // TODO: batch
      (user.isConnected || user.connection == ConnectionStatus.Self, updated.isConnected) match {
        case (true, _) => onContactUpdated(user, updated)
        case (_, true) => updateContactName(updated)
        case _ => // not connected
      }
    }
  }

  override def getOrElseUpdate(id: UserId, default: => UserData): Future[UserData] = getOrCreate(id, default)

  override def listAll(ids: Traversable[UserId]) = getAll(ids).map(_.collect { case Some(x) => x }(breakOut))

  override def listSignal(ids: Traversable[UserId]): Signal[Vector[UserData]] = {
    val idSet = ids.toSet
    new RefreshingSignal(listAll(ids).lift, onChanged.map(_.filter(u => idSet(u.id))).filter(_.nonEmpty))
  }

  def listUsersByConnectionStatus(p: Set[ConnectionStatus]): Future[Map[UserId, UserData]] =
    find[(UserId, UserData), Map[UserId, UserData]](
      user => p(user.connection) && !user.deleted,
      db   => UserDataDao.findByConnectionStatus(p)(db),
      user => (user.id, user))

  def listAcceptedOrPendingUsers: Future[Map[UserId, UserData]] =
    find[(UserId, UserData), Map[UserId, UserData]](
      user => user.isAcceptedOrPending && !user.deleted,
      db   => UserDataDao.findByConnectionStatus(Set(ConnectionStatus.Accepted, ConnectionStatus.PendingFromOther, ConnectionStatus.PendingFromUser))(db),
      user => (user.id, user))

  def addOrOverwrite(user: UserData): Future[UserData] = updateOrCreate(user.id, _ => user, user)

  def onContactUpdated(user: UserData, updated: UserData) = if (user.name != updated.name) updateContactName(updated)

  private def updateContactName(user: UserData): CancellableFuture[Unit] = contactNameParts map { cs => updateContactName(user, cs) }

  private def updateContactName(user: UserData, cs: mutable.HashMap[UserId, NameParts]): NameParts = {
    val name = NameParts.parseFrom(user.name)

    // remove previous if different first name
    cs.get(user.id) foreach { n =>
      if (n.first != name.first) {
        contactsByName.removeBinding(n.first, user.id)
        displayNameUpdater ! n.first
      }
    }

    cs(user.id) = name
    contactsByName.addBinding(name.first, user.id)
    displayNameUpdater ! name.first
    name
  }

  val displayNameUpdater: SerialProcessingQueue[String] = new SerialProcessingQueue[String]({ firstNames =>
    contactNameParts map { cs =>
      firstNames.toSet foreach { (first: String) =>
        updateDisplayNamesWithSameFirst(contactsByName.getOrElse(first, Set()).toSeq, cs)
      }
    }
  }, "UsersDisplayNameUpdater")

  def updateDisplayNamesWithSameFirst(users: Seq[UserId], cs: mutable.HashMap[UserId, NameParts]): Unit = {
    def setFullName(user: UserId) = update(user, { (u : UserData) => u.copy(displayName = u.name) })
    def setDisplayName(user: UserId, name: String) = update(user, (_: UserData).copy(displayName = Name(name)))

    if (users.isEmpty) CancellableFuture.successful(())
    else if (users.size == 1) {
      val user = users.head
      cs.get(user).fold(setFullName(user))(name => setDisplayName(user, name.first))
    } else {
      def firstWithInitial(user: UserId) = cs.get(user).fold("")(_.firstWithInitial)

      users.groupBy(firstWithInitial) map {
        case ("", us) => Future.sequence(us map setFullName)
        case (name, Seq(u)) => setDisplayName(u, name)
        case (name, us) => Future.sequence(us map setFullName)
      }
    }
  }

  override def getByTeam(teams: Set[TeamId]) =
    find(data => data.teamId.exists(id => teams.contains(id)), UserDataDao.findForTeams(teams)(_), identity)

  override def findUsersForService(id: IntegrationId) =
    find(_.integrationId.contains(id), UserDataDao.findService(id)(_), identity).map(_.toSet)

  override def searchByTeam(team: TeamId, prefix: SearchKey, handleOnly: Boolean) = storage(UserDataDao.search(prefix, handleOnly, Some(team))(_)).future
}
