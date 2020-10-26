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
package com.waz.service.otr

import com.waz.log.LogSE._
import com.waz.api.Verification
import com.waz.content.UserPreferences.LastSelfClientsSyncRequestedTime
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.model.otr.{Client, ClientId, UserClients}
import com.waz.service.AccountsService.Active
import com.waz.service.EventScheduler.Stage
import com.waz.service._
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.OtrClient
import com.waz.utils._
import com.wire.signals.Signal

import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.concurrent.duration._

trait OtrClientsService {

  val lastSelfClientsSyncPref: Preferences.Preference[Long]
  val otrClientsProcessingStage: Stage.Atomic

  def requestSyncIfNeeded(retryInterval: FiniteDuration = 7.days): Unit
  def getClient(id: UserId, client: ClientId): Future[Option[Client]]
  def getOrCreateClient(id: UserId, client: ClientId): Future[Client]
  def updateUserClients(user: UserId, clients: Seq[Client], replace: Boolean = false): Future[UserClients]
  def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean = false): Future[Set[UserClients]]
  def onCurrentClientRemoved(): Future[Option[(UserClients, UserClients)]]
  def removeClients(user: UserId, clients: Seq[ClientId]): Future[Option[(UserClients, UserClients)]]
  def updateClientLabel(id: ClientId, label: String): Future[Option[SyncId]]
  def selfClient: Signal[Client]
  def getSelfClient: Future[Option[Client]]
  def updateUnknownToUnverified(userId: UserId): Future[Unit]
}


class OtrClientsServiceImpl(selfId:    UserId,
                            clientId:  ClientId,
                            netClient: OtrClient,
                            userPrefs: UserPreferences,
                            storage:   OtrClientsStorage,
                            sync:      SyncServiceHandle,
                            accounts:  AccountsService) extends OtrClientsService with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  override lazy val lastSelfClientsSyncPref: Preferences.Preference[Long] = userPrefs.preference(LastSelfClientsSyncRequestedTime)

  accounts.accountState(selfId) {
    case _: Active => requestSyncIfNeeded()
    case _ =>
  }

  override val otrClientsProcessingStage: Stage.Atomic = EventScheduler.Stage[OtrClientEvent] { (convId, events) =>
    RichFuture.traverseSequential(events) {
      case OtrClientAddEvent(client) =>
        for {
          _  <- updateUserClients(selfId, Seq(client))
          id <- sync.syncPreKeys(selfId, Set(client.id))
        } yield id
      case OtrClientRemoveEvent(cId) =>
        removeClients(selfId, Seq(cId))
    }
  }

  override def requestSyncIfNeeded(retryInterval: FiniteDuration = 7.days): Unit =
    getSelfClient.zip(lastSelfClientsSyncPref()) flatMap {
      case (Some(c), t) if t > System.currentTimeMillis() - retryInterval.toMillis => Future.successful(())
      case _ =>
        sync.syncSelfClients() flatMap { _ =>
          lastSelfClientsSyncPref := System.currentTimeMillis()
        }
    }

  override def getClient(id: UserId, client: ClientId): Future[Option[Client]] = storage.get(id) map { _.flatMap(_.clients.get(client)) }

  override def getOrCreateClient(id: UserId, client: ClientId): Future[Client] = {
    storage.get(id) flatMap {
      case Some(uc) if uc.clients.contains(client) => Future.successful(uc.clients(client))
      case _ =>
        def create = UserClients(id, Map(client -> Client(client, "")))
        def update(uc: UserClients) = uc.copy(clients = uc.clients.updated(client, uc.clients.getOrElse(client, Client(client, ""))))
        storage.updateOrCreate(id, update, create) flatMap { ucs =>
          val res = ucs.clients(client)
          if (res.isVerified) Future successful res
          else VerificationStateUpdater.awaitUpdated(selfId) map { _ => res } // synchronize with verification state processing to ensure that OTR_UNVERIFIED message is added before anything else
        }
    }
  }

  override def updateUserClients(user: UserId, clients: Seq[Client], replace: Boolean = false): Future[UserClients] = {
    verbose(l"updateUserClients($user, $clients, $replace)")
    updateClients(Map(user -> clients), replace).map(_.head)
  }

  override def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean = false): Future[Set[UserClients]] = {

    // request clients location sync if some location has no name
    // location will be present only for self clients, but let's check that just to be explicit
    def needsLocationSync(selfId: UserId, uss: Traversable[UserClients]): Boolean = {
      val needsSync = uss.filter(_.clients.values.exists(_.regLocation.exists(!_.hasName)))
      needsSync.nonEmpty && needsSync.exists(_.user == selfId)
    }

    verbose(l"updateUserClients(${ucs.map { case (id, cs) => id -> cs.size }}, $replace)")
    for {
      updated <- storage.updateClients(ucs, replace)
      _ <- if (needsLocationSync(selfId, updated)) sync.syncClientsLocation() else Future.successful({})
    } yield updated
  }

  def onCurrentClientRemoved(): Future[Option[(UserClients, UserClients)]] = storage.update(selfId, _ - clientId)

  override def removeClients(user: UserId, clients: Seq[ClientId]): Future[Option[(UserClients, UserClients)]] =
    storage.update(user, { cs =>
      cs.copy(clients = cs.clients -- clients)
    })

  override def updateClientLabel(id: ClientId, label: String): Future[Option[SyncId]] =
    storage.update(selfId, { cs =>
      cs.clients.get(id).fold(cs) { client =>
        cs.copy(clients = cs.clients.updated(id, client.copy(label = label)))
      }
    }) flatMap {
      case Some(_) =>
        verbose(l"clientLabel updated, client: $id")
        sync.postClientLabel(id, label).map(Option(_))
      case None =>
        verbose(l"client label was not updated $id")
        Future.successful(Option.empty[SyncId])
    }

  override def selfClient: Signal[Client] = for {
    uc <- storage.signal(selfId)
    res <- Signal.const(uc.clients.get(clientId)).collect { case Some(c) => c }
  } yield res

  override def getSelfClient: Future[Option[Client]] =
    storage.get(selfId).map {
      case Some(cs) =>
        verbose(l"self clients: $cs, clientId: $clientId")
        cs.clients.get(clientId)
      case _ => None
    }

  override def updateUnknownToUnverified(userId: UserId): Future[Unit] =
    storage.update(userId, { uc =>
      uc.copy(clients = uc.clients.map{ client =>
        if (client._2.verified == Verification.UNKNOWN)
          (client._1, client._2.copy(verified = Verification.UNVERIFIED))
        else
          client
      })
    }).map(_ => ())
}
