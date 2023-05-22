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
package com.waz.service.push

import java.io.IOException

import com.waz.api.NetworkMode
import com.waz.content.{AccountStorage, GlobalPreferences}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.otr.ClientId
import com.waz.model.{PushToken, PushTokenRemoveEvent, UserId}
import com.waz.service.AccountsService.Active
import com.waz.service.ZMessaging.accountTag
import com.waz.service._
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.{ErrorOr, PushTokenClient}
import com.wire.signals.{CancellableFuture, SerialDispatchQueue}
import com.wire.signals.{EventContext, Signal}
import com.waz.utils.wrappers.GoogleApi
import com.waz.utils.{Backoff, ExponentialBackoff, RichEither, returning}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Responsible for deciding when to generate and register push tokens and whether they should be active at all.
  */
class PushTokenService(userId:       UserId,
                       clientId:     ClientId,
                       globalToken:  GlobalTokenService,
                       accounts:     AccountsService,
                       accStorage:   AccountStorage,
                       client:       PushTokenClient,
                       sync:         SyncServiceHandle)(implicit accountContext: AccountContext) {

  implicit lazy val logTag: LogTag = accountTag[PushTokenService](userId)

  implicit val dispatcher = SerialDispatchQueue(name = "PushTokenService")

  private val isLoggedIn = accounts.accountState(userId).map {
    case _: Active => true
    case _         => false
  }
  private val userToken = accStorage.signal(userId).map(_.pushToken)

  val eventProcessingStage = EventScheduler.Stage[PushTokenRemoveEvent] ({ (_, events, tag) =>
    verbose(l"SSSTAGES<TAG:$tag> PushTokenService stage 1")
    globalToken.resetGlobalToken(events.map(_.token))
  },
    name = "PushTokenService - PushTokenRemoveEvent"
  )

  //on dispatcher prevents infinite register loop
  (for {
    true        <- isLoggedIn
    userToken   <- userToken
    globalToken <- globalToken.currentToken
  } yield (globalToken, userToken)).on(dispatcher) {
    case (Some(glob), Some(user)) if glob != user =>
      sync.deletePushToken(user)
      sync.registerPush(glob)
    case (Some(glob), None) =>
      sync.registerPush(glob)
    case (None, Some(user)) =>
      sync.deletePushToken(user)
    case _ => //do nothing
  }

  /**
    * @return Future(Right(true)) if we had to re-register the token (and thus should also perform a fetch. False
    *         indicates no need for a fetch
    */
  def checkCurrentUserTokens(): ErrorOr[Boolean] =
    for {
      res <- client.getPushTokens().future
      tok <- res.flatMapFuture(ts => userToken.head.map(t => Right((t, ts))))
      res <- tok match {
        case Right((Some(localUserToken), backendUserTokens)) =>
          val backendUserToken = backendUserTokens.find(_.clientId == clientId).map(_.token)
          if (backendUserToken.contains(localUserToken)) {
            verbose(l"Current device push token is already registered with the backend")
            Future.successful(Right(false))
          }
          else {
            verbose(l"Current device push token is not registered with the Backend! Will re-register")
            //There is no matching push token for this client on the backend, we need to re-register this user with the
            //current device token. We can do this by wiping the current value saved for this user. The subscription above
            //will then re-evaluate and re-register the token.
            accStorage.update(userId, _.copy(pushToken = None)).map(_ => Right(true))
          }

        case Right(_) => Future.successful(Right(false))
        case Left(err) => Future.successful(Left(err))
      }
    } yield res

  def onTokenRegistered(token: PushToken): Future[Unit] = {
    verbose(l"onTokenRegistered: $userId, $token")
    (for {
      true <- isLoggedIn.head
      _    <- accStorage.update(userId, _.copy(pushToken = Some(token)))
    } yield {}).recover {
      case _ => warn(l"account was not logged in after token sync completed")
    }
  }
}

trait GlobalTokenService {
  def currentToken: Signal[Option[PushToken]]
  def resetGlobalToken(toRemove: Vector[PushToken] = Vector.empty): Future[Unit]
  def setNewToken(): Future[Unit]
}

class GlobalTokenServiceImpl(googleApi: GoogleApi,
                             prefs:     GlobalPreferences,
                             network:   NetworkModeService) extends GlobalTokenService with DerivedLogTag {
  import PushTokenService._

  private implicit val dispatcher = SerialDispatchQueue(name = "GlobalTokenService")

//  val pushEnabled  = prefs.preference(PushEnabledKey) //TODO delete the push token if the PushEnabledKey is false
  val _currentToken = prefs.preference(GlobalPreferences.PushToken)
  override val currentToken = _currentToken.signal

  private var settingToken = Future.successful({})
  private var deletingToken = Future.successful({})

  for {
    play    <- googleApi.isGooglePlayServicesAvailable
    current <- _currentToken.signal
    network <- network.networkMode
  } if (play && current.isEmpty && network != NetworkMode.OFFLINE) setNewToken()

  //Specify empty to force remove all tokens, or else only remove if `toRemove` contains the current token.
  override def resetGlobalToken(toRemove: Vector[PushToken] = Vector.empty) = {
    _currentToken().flatMap {
      case Some(t) if toRemove.contains(t) || toRemove.isEmpty =>
        if (deletingToken.isCompleted) {
          deletingToken = for {
            _ <- retry({
              googleApi.deleteAllPushTokens()
            })
            _ <- _currentToken := None
          } yield {}
        }
        deletingToken
      case _ => Future.successful({})
    }
  }

  override def setNewToken() = {
    if (settingToken.isCompleted) {
      settingToken = for {
        t <- retry(returning(googleApi.getPushToken)(t => verbose(l"Setting new push token: $t")))
        _ <- _currentToken := Some(t)
      } yield {}
    }
    settingToken
  }

  private def retry[A](f: => A, attempts: Int = 0): Future[A] = {
    returning(dispatcher(f).future.recoverWith {
      case ex: IOException =>
        error(l"Failed action on google APIs, probably due to server connectivity error, will retry again", ex)
        for {
          _ <- CancellableFuture.delay(ResetBackoff.delay(attempts)).future
          _ <- network.isOnline.onTrue
          t <- retry(f, attempts + 1)
        } yield t
    })(_.failed.foreach(throw _))
  }
}

object PushTokenService {
  val logAfterAttempts = 5
  var ResetBackoff: Backoff = new ExponentialBackoff(1000.millis, 10.seconds)
  case class PushSenderId(str: String) extends AnyVal
}
