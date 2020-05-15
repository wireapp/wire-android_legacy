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

import android.content.Context
import android.location.Geocoder
import com.waz.api.Verification
import com.waz.api.impl.ErrorResponse
import com.waz.content.OtrClientsStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.UserId
import com.waz.model.otr.{Client, ClientId, Location, UserClients}
import com.waz.service.otr.OtrService.SessionId
import com.waz.service.otr._
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Retry, Success}
import com.waz.sync.client.OtrClient
import com.waz.threading.Threading
import com.waz.utils.Locales

import scala.collection.breakOut
import scala.concurrent.Future
import scala.util.Try

trait OtrClientsSyncHandler {
  def syncClients(user: UserId): Future[SyncResult]
  def postLabel(id: ClientId, label: String): Future[SyncResult]
  def syncPreKeys(clients: Map[UserId, Seq[ClientId]]): Future[SyncResult]
  def syncClientsLocation(): Future[SyncResult]

  def syncSessions(clients: Map[UserId, Seq[ClientId]]): Future[Option[ErrorResponse]]
}

class OtrClientsSyncHandlerImpl(context:    Context,
                                userId:     UserId,
                                selfClient:   ClientId,
                                netClient:  OtrClient,
                                otrClients: OtrClientsService,
                                storage:    OtrClientsStorage,
                                cryptoBox:  CryptoBoxService)
  extends OtrClientsSyncHandler
    with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  private lazy val sessions = cryptoBox.sessions

  def syncClients(user: UserId): Future[SyncResult] = {
    verbose(l"syncClients: $user")

    def hasSession(user: UserId, client: ClientId) = sessions.getSession(SessionId(user, client)).map(_.isDefined)

    def loadClients = (if (user == userId) netClient.loadClients() else netClient.loadClients(user)).future

    def withoutSession(clients: Iterable[ClientId]) =
      Future.traverse(clients) { client =>
        if (selfClient == client) Future successful None
        else hasSession(user, client) map { if (_) None else Some(client) }
      } map { _.flatten.toSeq }

    def syncSessionsIfNeeded(clients: Iterable[ClientId]) =
      for {
        toSync <- withoutSession(clients)
        err    <- if (toSync.isEmpty) Future.successful(None) else syncSessions(Map(user -> toSync))
      } yield
        err.fold[SyncResult](Success)(SyncResult(_))

    def updatePreKeys(id: ClientId) =
      netClient.loadRemainingPreKeys(id).future.flatMap {
        case Right(ids) =>
          cryptoBox.generatePreKeysIfNeeded(ids).flatMap {
            case keys if keys.isEmpty => Future.successful(Success)
            case keys                 => netClient.updateKeys(id, Some(keys)).future map {
              case Right(_)    => Success
              case Left(error) => SyncResult(error)
            }
          }
        case Left(error) => Future.successful(SyncResult(error))
      }

    loadClients.flatMap {
      case Left(error)    => Future.successful(SyncResult(error))
      case Right(clients) =>
        val userClients =
          if (user == userId)
            clients.map(c => if (selfClient == c.id) c.copy(verified = Verification.VERIFIED) else c)
          else
            clients

        for {
          ucs <- otrClients.updateUserClients(user, userClients, replace = true)
          _   <- syncSessionsIfNeeded(ucs.clients.keys)
          res <- updatePreKeys(selfClient)
          _   <- res match {
                   case Success => otrClients.lastSelfClientsSyncPref := System.currentTimeMillis()
                   case _       => Future.successful({})
                 }
        } yield res
    }
  }

  def postLabel(id: ClientId, label: String): Future[SyncResult] =
    netClient.postClientLabel(id, label).future map {
      case Right(_)  => Success
      case Left(err) => SyncResult(err)
    }

  def syncPreKeys(clients: Map[UserId, Seq[ClientId]]): Future[SyncResult] = syncSessions(clients).map {
    case Some(error) => SyncResult(error)
    case None        => Success
  }

  def syncSessions(clients: Map[UserId, Seq[ClientId]]): Future[Option[ErrorResponse]] =
    netClient.loadPreKeys(clients).future
      .flatMap {
        case Left(error) => Future.successful(Some(error))
        case Right(us)   =>
          for {
            _       <- otrClients.updateClients(us.mapValues(_.map { case (id, key) => Client(id, "") }))
            prekeys =  us.flatMap { case (u, cs) => cs map { case (c, p) => (SessionId(u, c), p)} }
            _       <- Future.traverse(prekeys) { case (id, p) => sessions.getOrCreateSession(id, p) }
            _       <- VerificationStateUpdater.awaitUpdated(userId)
          } yield None
      }.recover {
        case e: Throwable => Some(ErrorResponse.internalError(e.getMessage))
      }

  def syncClientsLocation(): Future[SyncResult] = {
    import scala.collection.JavaConverters._

    def loadName(lat: Double, lon: Double) = Future {
      val geocoder = new Geocoder(context, Locales.currentLocale)
      Try(geocoder.getFromLocation(lat, lon, 1).asScala).toOption.flatMap(_.headOption).flatMap { add =>
        Option(Seq(Option(add.getLocality), Option(add.getCountryCode)).flatten.mkString(", ")).filter(_.nonEmpty)
      }
    } (Threading.IO)

    def loadNames(locs: Iterable[Location]) =
      Future.traverse(locs) { l => loadName(l.lat, l.lon).map { (l.lat, l.lon) -> _ } }

    def updateClients(locs: Map[(Double, Double), String])(ucs: UserClients) =
      ucs.copy(clients = ucs.clients.mapValues { c =>
        c.regLocation.flatMap { l =>
          locs.get((l.lat, l.lon)).map(n => l.copy(name = n))
        }.fold(c) { loc => c.copy(regLocation = Some(loc)) }
      })

    storage.get(userId) flatMap {
      case None =>
        Future.successful(Success)
      case Some(ucs) =>
        val toSync = ucs.clients.values collect {
          case Client(_, _, _, _, Some(loc), _, _, _, _) if !loc.hasName => loc
        }
        if (toSync.isEmpty)
          Future.successful(Success)
        else
          for {
            ls <- loadNames(toSync)
            locations: Map[(Double, Double), String] = ls.collect { case (k, Some(name)) => k -> name }(breakOut)
            update <- storage.update(userId, updateClients(locations))
          } yield {
            update match {
              case Some((_, UserClients(_, cs))) if cs.values.forall(_.regLocation.forall(_.hasName)) => Success
              case _ =>
                Retry(s"user clients were not updated, locations: $locations, toSync: $toSync")
            }
          }
    }
  }
}

