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
import com.waz.model.otr.{Client, ClientId}
import com.waz.model.{QualifiedId, UserId}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr._
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Success
import com.waz.sync.client.{ErrorOr, OtrClient}
import com.wire.cryptobox.PreKey

import scala.collection.immutable.Map
import scala.concurrent.Future

trait OtrClientsSyncHandler {
  def syncClients(users: Set[QualifiedId]): Future[SyncResult]
  def syncClients(user: UserId): Future[SyncResult]
  def postLabel(id: ClientId, label: String): Future[SyncResult]
  def postCapabilities(): Future[SyncResult]
  def syncPreKeys(clients: Map[UserId, Seq[ClientId]]): Future[SyncResult]
  def syncSessions(clients: Map[UserId, Seq[ClientId]]): Future[Option[ErrorResponse]]
}

class OtrClientsSyncHandlerImpl(selfId:     UserId,
                                selfClient: ClientId,
                                netClient:  OtrClient,
                                otrClients: OtrClientsService,
                                cryptoBox:  CryptoBoxService,
                                userPrefs:  UserPreferences)
  extends OtrClientsSyncHandler
    with DerivedLogTag { self =>

  import com.waz.threading.Threading.Implicits.Background

  private lazy val sessions = cryptoBox.sessions

  private def hasSession(user: UserId, client: ClientId) =
    sessions.getSession(SessionId(user, None, client)).map(_.isDefined)

  private def withoutSession(userId: UserId, clients: Iterable[ClientId]) =
    Future.traverse(clients) { client =>
      if (selfClient == client) Future successful None
      else hasSession(userId, client) map { if (_) None else Some(client) }
    } map { _.flatten.toSeq }

  private def updateClients(users: Map[UserId, Seq[Client]]): Future[SyncResult] = {
    def withoutSession(): Future[Map[UserId, Seq[ClientId]]] =
      Future.sequence(
        users.map { case (id, clients) => self.withoutSession(id, clients.map(_.id)).map(cs => id -> cs) }
      ).map(_.toMap)

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

    val (selfClients, otherClients) = users.partition(_._1 == selfId)
    val userClients =
      otherClients ++ selfClients.map {
        case (id, clients) => id -> clients.map(c => if (selfClient == c.id) c.copy(verified = Verification.VERIFIED) else c)
      }

    for {
      _   <- otrClients.updateUserClients(userClients, replace = true)
      res <- syncSessionsIfNeeded()
      res <- if (SyncResult.isSuccess(res)) updatePreKeys(selfClient) else Future.successful(res)
      _   <- res match {
        case Success => otrClients.lastSelfClientsSyncPref := System.currentTimeMillis()
        case _       => Future.successful({})
      }
    } yield res
  }

  override def syncClients(userId: UserId): Future[SyncResult] =
    ((if (userId == selfId) netClient.loadClients() else netClient.loadClients(userId)).future)
      .flatMap {
        case Left(error)    => Future.successful(SyncResult(error))
        case Right(clients) => updateClients(Map(userId -> clients))
      }

  override def syncClients(users: Set[QualifiedId]): Future[SyncResult] = {
    val (qualified, unqualified) = users.partition(_.hasDomain)

    val qualifiedSync =
      if (qualified.nonEmpty) syncQualified(qualified)
      else Future.successful(SyncResult.Success)
    val unqualifiedSync =
      if (unqualified.nonEmpty) syncUnqualified(unqualified.map(_.id))
      else Future.successful(SyncResult.Success)

    qualifiedSync.flatMap {
      case SyncResult(error) => Future.successful(SyncResult(error))
      case _                 => unqualifiedSync
    }
  }

  private def syncQualified(users: Set[QualifiedId]): Future[SyncResult] =
    netClient.loadClients(users).future.flatMap {
      case Right(response) =>
        updateClients(response.map { case (QualifiedId(id, _), clients) => id -> clients })
      case Left(ErrorResponse.PageNotFound) =>
        // fallback to requesting clients per user
        syncUnqualified(users.map(_.id))
      case Left(error)=>
        Future.successful(SyncResult(error))
    }

  private def syncUnqualified(users: Set[UserId]): Future[SyncResult] =
    Future
      .sequence(users.map { id => netClient.loadClients(id).future.map(id -> _) })
      .flatMap { responses =>
        val error = responses.collectFirst { case (_, Left(error)) => error }
        error match {
          case Some(err) =>
            Future.successful(SyncResult(err))
          case None =>
            updateClients(responses.collect { case (id, Right(clients)) => id -> clients }.toMap)
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

  override def syncPreKeys(clients: Map[UserId, Seq[ClientId]]): Future[SyncResult] = syncSessions(clients).map {
    case Some(error) => SyncResult(error)
    case None        => Success
  }

  override def syncSessions(clients: Map[UserId, Seq[ClientId]]): Future[Option[ErrorResponse]] =
    loadPreKeys(clients).flatMap {
      case Left(error) => Future.successful(Some(error))
      case Right(us)   =>
        for {
          _       <- otrClients.updateUserClients(
                       us.map { case (uId, cs) => uId -> cs.map { case (id, _) => Client(id) } }, replace = false
                     )
          prekeys =  us.flatMap { case (u, cs) => cs map { case (c, p) => (SessionId(u, None, c), p)} }
          _       <- Future.traverse(prekeys) { case (id, p) => sessions.getOrCreateSession(id, p) }
          _       <- VerificationStateUpdater.awaitUpdated(selfId)
        } yield None
    }.recover {
      case e: Throwable => Some(ErrorResponse.internalError(e.getMessage))
    }

  private def loadPreKeys(clients: Map[UserId, Seq[ClientId]]) = {
    import OtrClientsSyncHandlerImpl.LoadPreKeysMaxClients

    def mapSize(map: Map[UserId, Seq[ClientId]]): Int = map.values.map(_.size).sum
    def load(map: Map[UserId, Seq[ClientId]]): ErrorOr[Map[UserId, Seq[(ClientId, PreKey)]]] = netClient.loadPreKeys(map).future

    // request accepts up to 128 clients, we should make sure not to send more
    if (mapSize(clients) < LoadPreKeysMaxClients) load(clients)
    else {
      // we divide the original map into a list of chunks, each with at most 127 clients
      val chunks =
        clients.foldLeft(List(Map.empty[UserId, Seq[ClientId]])) { case (acc, (userId, clientIds)) =>
          val currentMap = acc.head
          if (mapSize(currentMap) + clientIds.size < LoadPreKeysMaxClients)
            (currentMap + (userId -> clientIds)) :: acc.tail
          else
            Map(userId -> clientIds) :: acc
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
}

object OtrClientsSyncHandlerImpl {
  val LoadPreKeysMaxClients = 128
}
