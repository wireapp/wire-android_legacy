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

import com.waz.ZLog
import com.waz.ZLog._
import com.waz.api.NetworkMode
import com.waz.content.{AccountStorage, GlobalPreferences}
import com.waz.model.{PushToken, PushTokenRemoveEvent, UserId}
import com.waz.service.AccountsService.Active
import com.waz.service.ZMessaging.accountTag
import com.waz.service._
import com.waz.service.tracking.TrackingService.exception
import com.waz.sync.SyncServiceHandle
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.GoogleApi
import com.waz.utils.{Backoff, ExponentialBackoff, returning}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

/**
  * Responsible for deciding when to generate and register push tokens and whether they should be active at all.
  */
class PushTokenService(userId:       UserId,
                       globalToken:  GlobalTokenService,
                       accounts:     AccountsService,
                       accStorage:   AccountStorage,
                       sync:         SyncServiceHandle)(implicit accountContext: AccountContext) {

  implicit lazy val logTag: LogTag = accountTag[PushTokenService](userId)

  implicit val dispatcher = new SerialDispatchQueue(name = "PushTokenService")

  private val isLoggedIn = accounts.accountState(userId).map {
    case _: Active => true
    case _         => false
  }
  private val userToken = accStorage.signal(userId).map(_.pushToken)

  val eventProcessingStage = EventScheduler.Stage[PushTokenRemoveEvent] { (_, events) =>
    globalToken.resetGlobalToken(events.map(_.token))
  }

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

  def onTokenRegistered(token: PushToken): Future[Unit] = {
    verbose(s"onTokenRegistered: $userId, $token")
    (for {
      true <- isLoggedIn.head
      _    <- accStorage.update(userId, _.copy(pushToken = Some(token)))
    } yield {}).recover {
      case _ => warn("account was not logged in after token sync completed")
    }
  }
}

trait GlobalTokenService {
  def currentToken: Signal[Option[PushToken]]
  def resetGlobalToken(toRemove: Vector[PushToken] = Vector.empty): Future[Unit]
  def setNewToken(): Future[Unit]
}

class GlobalTokenServiceImpl(googleApi: GoogleApi,
                             prefs: GlobalPreferences,
                             network: NetworkModeService) extends GlobalTokenService {
  import PushTokenService._
  import ZLog.ImplicitTag._

  implicit val dispatcher = new SerialDispatchQueue(name = "GlobalTokenService")
  implicit val ev = EventContext.Global

//  val pushEnabled  = prefs.preference(PushEnabledKey) //TODO delete the push token if the PushEnabledKey is false
  val _currentToken = prefs.preference(GlobalPreferences.PushToken)
  override val currentToken = _currentToken.signal
  private val resetToken    = prefs.preference(GlobalPreferences.ResetPushToken)

  private var settingToken = Future.successful({})
  private var deletingToken = Future.successful({})

  for {
    play    <- googleApi.isGooglePlayServicesAvailable
    current <- _currentToken.signal
    network <- network.networkMode
  } if (play && current.isEmpty && network != NetworkMode.OFFLINE) setNewToken()

  for {
    play    <- googleApi.isGooglePlayServicesAvailable
    reset   <- resetToken.signal
    network <- network.networkMode
  } if (play && reset && network != NetworkMode.OFFLINE) resetGlobalToken()

  //Specify empty to force remove all tokens, or else only remove if `toRemove` contains the current token.
  override def resetGlobalToken(toRemove: Vector[PushToken] = Vector.empty) = {
    verbose("resetGlobalToken")
    _currentToken().flatMap {
      case Some(t) if toRemove.contains(t) || toRemove.isEmpty =>
        if (deletingToken.isCompleted) {
          deletingToken = for {
            _ <- retry({
              verbose("Deleting all push tokens")
              googleApi.deleteAllPushTokens()
            })
            _ <- resetToken := false
            _ <- _currentToken := None
          } yield {}
        }
        deletingToken
      case _ => Future.successful({})
    }
  }

  override def setNewToken() = {
    verbose("setNewToken")
    if (settingToken.isCompleted) {
      settingToken = for {
        t <- retry(returning(googleApi.getPushToken) { t => verbose(s"Setting new push token: $t") })
        _ <- _currentToken := Some(t)
      } yield {}
    }
    settingToken
  }

  private def retry[A](f: => A, attempts: Int = 0): Future[A] = {
    returning(dispatcher(f).future.recoverWith {
      case ex: IOException =>
        error(s"Failed action on google APIs, probably due to server connectivity error, will retry again", ex)
        for {
          _ <- if (attempts % logAfterAttempts == 0) exception(new Exception("Too many push token registration attempts") with NoStackTrace, s"Failed to register an FCM push token after $logAfterAttempts attempts") else Future.successful({})
          _ <- CancellableFuture.delay(ResetBackoff.delay(attempts)).future
          _ <- network.networkMode.filter(_ != NetworkMode.OFFLINE).head
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
