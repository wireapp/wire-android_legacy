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
package com.waz.sync.otr

import com.waz.api.Verification
import com.waz.api.impl.ErrorResponse
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences.ShouldPostClientCapabilities
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.otr.{Client, ClientId, OtrClientIdMap, QOtrClientIdMap}
import com.waz.model.{Domain, QualifiedId, UserId}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr._
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Success
import com.waz.sync.client.{ErrorOr, OtrClient}
import com.waz.zms.BuildConfig
import com.wire.cryptobox.PreKey

import scala.collection.immutable.Map
import scala.concurrent.Future

trait OtrClientsSyncHandler {
  def syncClients(users: Set[QualifiedId]): Future[SyncResult]
  def syncSelfClients(): Future[SyncResult]
  def postLabel(id: ClientId, label: String): Future[SyncResult]
  def postCapabilities(): Future[SyncResult]
  def syncPreKeys(clients: QOtrClientIdMap): Future[SyncResult]
  def syncSessions(clients: QOtrClientIdMap): Future[Option[ErrorResponse]]
}

class OtrClientsSyncHandlerImpl(selfId:        UserId,
                                currentDomain: Domain,
                                selfClient:    ClientId,
                                netClient:     OtrClient,
                                otrClients:    OtrClientsService,
                                cryptoBox:     CryptoBoxService,
                                userPrefs:     UserPreferences)
  extends OtrClientsSyncHandler with DerivedLogTag { self =>
  import OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients
  import com.waz.threading.Threading.Implicits.Background

  private lazy val sessions = cryptoBox.sessions

  private def hasSession(qId: QualifiedId, clientId: ClientId) =
    sessions.getSession(SessionId(qId, clientId, currentDomain)).map(_.isDefined)

  private def withoutSession(qId: QualifiedId, clients: Iterable[ClientId]) =
    Future.traverse(clients) { clientId =>
      if (selfClient == clientId) Future successful None
      else hasSession(qId, clientId) map { if (_) None else Some(clientId) }
    } map { _.flatten.toSeq }

  private def updateClients(users: Map[QualifiedId, Seq[Client]]): Future[SyncResult] = {
    def withoutSession(): Future[QOtrClientIdMap] =
      Future.sequence(
        users.map { case (id, clients) => self.withoutSession(id, clients.map(_.id)).map(cs => id -> cs.toSet) }
      ).map(QOtrClientIdMap(_))

    def syncSessionsIfNeeded() =
      for {
        toSync <- withoutSession()
        err    <- if (toSync.isEmpty) Future.successful(None) else syncSessions(toSync)
      } yield
        err.fold[SyncResult](Success)(SyncResult(_))

    def updatePreKeys(clientId: ClientId) =
      netClient.loadRemainingPreKeys(clientId).future.flatMap {
        case Right(ids) =>
          cryptoBox.generatePreKeysIfNeeded(ids).flatMap {
            case keys if keys.isEmpty => Future.successful(Success)
            case keys                 => netClient.updateKeys(clientId, Some(keys)).future map {
              case Right(_)    => Success
              case Left(error) => SyncResult(error)
            }
          }
        case Left(error) => Future.successful(SyncResult(error))
      }

    val (selfClients, otherClients) = users.partition(_._1.id == selfId)
    val userClients =
      otherClients ++ selfClients.map {
        case (id, clients) => id -> clients.map(c => if (selfClient == c.id) c.copy(verified = Verification.VERIFIED) else c)
      }

    for {
      _   <- otrClients.updateUserClients(userClients.map { case (QualifiedId(id, _), c) => id -> c }, replace = true)
      res <- syncSessionsIfNeeded()
      res <- if (SyncResult.isSuccess(res)) updatePreKeys(selfClient) else Future.successful(res)
      _   <- res match {
        case Success => otrClients.lastSelfClientsSyncPref := System.currentTimeMillis()
        case _       => Future.successful({})
      }
    } yield res
  }

  override def syncSelfClients(): Future[SyncResult] =
    netClient.loadClients().future
      .flatMap {
        case Left(error)    => Future.successful(SyncResult(error))
        case Right(clients) => updateClients(Map(QualifiedId(selfId, currentDomain.str) -> clients))
      }

  override def syncClients(users: Set[QualifiedId]): Future[SyncResult] = {
    val (qualified, nonQualified) = users.partition(_.hasDomain)

    val qualifiedSync =
      if (qualified.nonEmpty) syncQualified(qualified)
      else Future.successful(SyncResult.Success)
    val unqualifiedSync =
      if (nonQualified.nonEmpty) syncNonQualified(nonQualified.map(_.id))
      else Future.successful(SyncResult.Success)

    qualifiedSync.flatMap {
      case SyncResult(error) => Future.successful(SyncResult(error))
      case _                 => unqualifiedSync
    }
  }

  private def syncQualified(users: Set[QualifiedId]): Future[SyncResult] =
    netClient.loadClients(users).future.flatMap {
      case Right(response) =>
        updateClients(response)
      case Left(ErrorResponse.PageNotFound) =>
        // fallback to requesting clients per user
        syncNonQualified(users.map(_.id))
      case Left(error)=>
        Future.successful(SyncResult(error))
    }

  private def syncNonQualified(users: Set[UserId]): Future[SyncResult] =
    Future
      .sequence(users.map { id => netClient.loadClients(id).future.map(id -> _) })
      .flatMap { responses =>
        val error = responses.collectFirst { case (_, Left(error)) => error }
        error match {
          case Some(err) =>
            Future.successful(SyncResult(err))
          case None =>
            updateClients(responses.collect {
              case (id, Right(clients)) => QualifiedId(id, currentDomain.str) -> clients
            }.toMap)
        }
      }

  override def postLabel(id: ClientId, label: String): Future[SyncResult] =
    netClient.postClientLabel(id, label).future map {
      case Right(_)  => Success
      case Left(err) => SyncResult(err)
    }

  override def postCapabilities(): Future[SyncResult] =
    netClient.postClientCapabilities(selfClient).future.flatMap {
      case Right(_)  => (userPrefs.preference(ShouldPostClientCapabilities) := false).map(_ => Success)
      case Left(err) => (userPrefs.preference(ShouldPostClientCapabilities) := true).map(_ => SyncResult(err))
    }

  override def syncPreKeys(clients: QOtrClientIdMap): Future[SyncResult] = syncSessions(clients).map {
    case Some(error) => SyncResult(error)
    case None        => Success
  }

  override def syncSessions(clients: QOtrClientIdMap): Future[Option[ErrorResponse]] =
    if (BuildConfig.FEDERATION_USER_DISCOVERY) {
      loadPreKeys(clients).flatMap {
        case Left(error) => Future.successful(Some(error))
        case Right(qs)   =>
          for {
            _       <- otrClients.updateUserClients(
                         qs.map { case (QualifiedId(id, _), cs) => id -> cs.keys.map(Client(_)).toSeq },
                         replace = false
                       )
            prekeys =  qs.flatMap { case (qId, cs) => cs.map { case (c, p) => (SessionId(qId, c, currentDomain), p)} }
            _       <- Future.traverse(prekeys) { case (id, p) => sessions.getOrCreateSession(id, p) }
            _       <- VerificationStateUpdater.awaitUpdated(selfId)
          } yield None
      }.recover {
        case e: Throwable => Some(ErrorResponse.internalError(e.getMessage))
      }
  } else {
      syncSessions(OtrClientIdMap(clients.entries.map { case (qId, cs) => qId.id -> cs }))
    }

  private def syncSessions(clients: OtrClientIdMap): Future[Option[ErrorResponse]] =
    loadPreKeys(clients).flatMap {
      case Left(error) => Future.successful(Some(error))
      case Right(us)   =>
        for {
          _       <- otrClients.updateUserClients(
            us.map { case (uId, cs) => uId -> cs.map { case (id, _) => Client(id) } },
            replace = false
          )
          prekeys =  us.flatMap { case (u, cs) => cs map { case (c, p) => (SessionId(u, Domain.Empty, c), p)} }
          _       <- Future.traverse(prekeys) { case (id, p) => sessions.getOrCreateSession(id, p) }
          _       <- VerificationStateUpdater.awaitUpdated(selfId)
        } yield None
    }.recover {
      case e: Throwable => Some(ErrorResponse.internalError(e.getMessage))
    }

  private def loadPreKeys(clients: OtrClientIdMap) = {
    def mapSize(map: OtrClientIdMap): Int = map.entries.values.map(_.size).sum
    def load(map: OtrClientIdMap): ErrorOr[Map[UserId, Seq[(ClientId, PreKey)]]] = netClient.loadPreKeys(map).future

    // request accepts up to 128 clients, we should make sure not to send more
    if (mapSize(clients) < LoadPreKeysMaxClients) load(clients)
    else {
      // we divide the original map into a list of chunks, each with at most 127 clients
      val chunks =
        clients.entries.foldLeft(List(OtrClientIdMap.Empty)) { case (acc, (userId, clientIds)) =>
          val currentMap = acc.head
          if (mapSize(currentMap) + clientIds.size < LoadPreKeysMaxClients)
            OtrClientIdMap(currentMap.entries + (userId -> clientIds)) :: acc.tail
          else
            OtrClientIdMap.from(userId -> clientIds) :: acc
        }

      // for each chunk we load the prekeys separately and then add them together (unless there's an error response)
      Future
        .sequence(chunks.map(load))
        .map { responses =>
          responses.find(_.isLeft).getOrElse {
            Right {
              responses
                .collect { case Right(prekeys) => prekeys }
                .reduce[Map[UserId, Seq[(ClientId, PreKey)]]] { case (p1, p2) => p1 ++ p2 }
            }
          }
        }
    }
  }

  private def loadPreKeys(clients: QOtrClientIdMap) = {
    def mapSize(map: QOtrClientIdMap): Int = map.entries.values.map(_.size).sum
    def load(map: QOtrClientIdMap): ErrorOr[Map[QualifiedId, Map[ClientId, PreKey]]] = netClient.loadPreKeys(map).future

    // request accepts up to 128 clients, we should make sure not to send more
    if (mapSize(clients) < LoadPreKeysMaxClients) load(clients)
    else {
      // we divide the original map into a list of chunks, each with at most 127 clients
      val chunks =
        clients.entries.foldLeft(List(QOtrClientIdMap.Empty)) { case (acc, (qualifiedId, clientIds)) =>
          val currentMap = acc.head
          if (mapSize(currentMap) + clientIds.size < LoadPreKeysMaxClients)
            QOtrClientIdMap(currentMap.entries + (qualifiedId -> clientIds)) :: acc.tail
          else
            QOtrClientIdMap.from(qualifiedId -> clientIds) :: acc
        }

      // for each chunk we load the prekeys separately and then add them together (unless there's an error response)
      Future
        .sequence(chunks.map(load))
        .map { responses =>
          responses.find(_.isLeft).getOrElse {
            Right {
              responses
                .collect { case Right(prekeys) => prekeys }
                .reduce[Map[QualifiedId, Map[ClientId, PreKey]]] { case (p1, p2) => p1 ++ p2 }
            }
          }
        }
    }
  }
}

object OtrClientsSyncHandlerImpl {
  val LoadPreKeysMaxClients = 128
}
