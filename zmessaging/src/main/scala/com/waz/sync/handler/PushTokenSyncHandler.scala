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
package com.waz.sync.handler

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.otr.ClientId
import com.waz.model.PushToken
import com.waz.service.BackendConfig
import com.waz.service.push.PushTokenService
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Retry
import com.waz.sync.client.PushTokenClient
import com.waz.sync.client.PushTokenClient.PushTokenRegistration
import com.wire.signals.{CancellableFuture, Signal}
import com.waz.threading.Threading

import scala.concurrent.Future

class PushTokenSyncHandler(pushTokenService: PushTokenService,
                           backend: Signal[BackendConfig],
                           clientId: ClientId,
                           client: PushTokenClient) extends DerivedLogTag {

  import Threading.Implicits.Background

  def registerPushToken(token: PushToken): Future[SyncResult] = {
    debug(l"registerPushToken: $token")
    val pushSenderId = backend.currentValue.map { b => b.pushSenderId}.getOrElse("") // the first value is guaranteed to have at least the push sender Id
    client.postPushToken(PushTokenRegistration(token, pushSenderId, clientId)).future.flatMap {
      case Right(PushTokenRegistration(`token`, _, `clientId`, _)) =>
        pushTokenService
          .onTokenRegistered(token)
          .map(_ => SyncResult.Success)
      case Right(_)  =>
        Future.successful(Retry("Unexpected response"))
      case Left(err) =>
        Future.successful(SyncResult(err))
    }
  }

  def deleteGcmToken(token: PushToken): CancellableFuture[SyncResult] = {
    debug(l"deleteGcmToken($token)")
    client.deletePushToken(token.str).map(SyncResult(_))
  }
}
