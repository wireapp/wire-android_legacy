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
import com.waz.log.LogSE._
import com.waz.api.Verification
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.model.otr.UserClients.UserClientsDao
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.Signal
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.collection.breakOut
import scala.collection.immutable.Map
import scala.concurrent.Future

trait OtrClientsStorage extends CachedStorage[UserId, UserClients] {
  def incomingClientsSignal(userId: UserId, clientId: ClientId): Signal[Seq[Client]]
  def getClients(user: UserId): Future[Seq[Client]]
  def updateVerified(userId: UserId, clientId: ClientId, verified: Boolean): Future[Option[(UserClients, UserClients)]]
  def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean = false): Future[Set[UserClients]]

}

class OtrClientsStorageImpl(userId: UserId, context: Context, storage: Database)
  extends CachedStorageImpl[UserId, UserClients](new TrimmingLruCache(context, Fixed(2000)), storage)(UserClientsDao, LogTag("OtrClientsStorage"))
    with OtrClientsStorage
    with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override def incomingClientsSignal(userId: UserId, clientId: ClientId) =
    signal(userId) map { ucs =>
      ucs.clients.get(clientId).flatMap(_.regTime).fold(Seq.empty[Client]) { current =>
        ucs.clients.values.filter(c => c.verified == Verification.UNKNOWN && c.regTime.exists(_.isAfter(current))).toVector
      }
    }

  override def getClients(user: UserId) = get(user).map(_.fold(Seq.empty[Client])(_.clients.values.toVector))

  override def updateVerified(userId: UserId, clientId: ClientId, verified: Boolean) = update(userId, { uc =>
    uc.clients.get(clientId) .fold (uc) { client =>
      uc.copy(clients = uc.clients + (client.id -> client.copy(verified = if (verified) Verification.VERIFIED else Verification.UNVERIFIED)))
    }
  })

  override def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean = false) = {

    def updateOrCreate(user: UserId, clients: Seq[Client]): (Option[UserClients] => UserClients) = {
      case Some(cs) =>
        val prev = cs.clients
        val updated: Map[ClientId, Client] = clients.map { c => c.id -> prev.get(c.id).fold(c)(_.updated(c)) }(breakOut)
        cs.copy(clients = if (replace) updated else prev ++ updated)
      case None =>
        UserClients(user, clients.map(c => c.id -> c)(breakOut))
    }

    verbose(l"updateClients: $ucs, replace = $replace")

    updateOrCreateAll(ucs.map { case (u, cs) => u -> updateOrCreate(u, cs) } (breakOut))
  }
}
