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

import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

import com.waz.log.LogSE._
import com.waz.log.BasicLogging.LogTag
import com.waz.model.UserId
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.accountTag
import com.waz.service.push.WSPushServiceImpl.RequestCreator
import com.waz.service.{BackendConfig, NetworkModeService}
import com.waz.sync.client.{AccessTokenProvider, PushNotificationEncoded}
import com.waz.sync.client.PushNotificationsClient.NotificationsResponseEncoded
import com.wire.signals.{CancellableFuture, SerialDispatchQueue}
import com.wire.signals._
import com.waz.utils.{Backoff, ExponentialBackoff}
import com.waz.sync.client.AuthenticationManager.AccessToken
import com.waz.sync.client
import com.waz.znet2.WebSocketFactory.SocketEvent
import com.waz.znet2.http.{Body, Method, Request}
import com.waz.znet2.{WebSocket, WebSocketFactory, http}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Left

trait WSPushService {
  def activate(initialDelay: FiniteDuration = 0.seconds): Unit
  def deactivate(): Unit
  val notifications: EventStream[Seq[PushNotificationEncoded]]
  val connected: Signal[Boolean]
}

object WSPushServiceImpl {

  type RequestCreator = AccessToken => Request[Body]

  def apply(userId: UserId,
            clientId: ClientId,
            backend: BackendConfig,
            webSocketFactory: WebSocketFactory,
            accessTokenProvider: AccessTokenProvider,
            networkModeService: NetworkModeService
           ): WSPushServiceImpl = {

    val requestCreator = (token: AccessToken) => {
      val webSocketUri = if(backend.websocketUrl.getPath.startsWith("/await")) backend.websocketUrl
        else backend.websocketUrl.buildUpon.appendPath("await").build

      val uri = webSocketUri.buildUpon.appendQueryParameter("client", clientId.str).build
      // XXX: this is a hack for Backend In The Box problem: 'Accept-Encoding: gzip' header causes 500
      val headers = token.headers + ("Accept-Encoding" -> "identity")

      Request.create(
        method = Method.Get,
        url = new URL(uri.toString),
        headers = http.Headers(headers)
      ).asInstanceOf[http.Request[http.Body]]
    }

    new WSPushServiceImpl(
      userId,
      accessTokenProvider,
      requestCreator,
      webSocketFactory,
      networkModeService,
      ExponentialBackoff.standardBackoff
    )
  }
}

class WSPushServiceImpl(userId:              UserId,
                        accessTokenProvider: AccessTokenProvider,
                        requestCreator:      RequestCreator,
                        webSocketFactory:    WebSocketFactory,
                        networkModeService:  NetworkModeService,
                        backoff:             Backoff = ExponentialBackoff.standardBackoff) extends WSPushService {

  private implicit val logTag: LogTag = accountTag[WSPushServiceImpl](userId)
  private implicit val dispatcher = SerialDispatchQueue(name = "WSPushServiceImpl")

  override val notifications: SourceStream[Seq[PushNotificationEncoded]] = EventStream()
  override val connected: SourceSignal[Boolean] = Signal(false)

  override def activate(initialDelay: FiniteDuration = 0.seconds): Unit = synchronized {
    verbose(l"(Re-)activate WebSocket process.")
    deactivate()
    currentWebSocketSubscription = Option(webSocketProcessEngine(initialDelay))
  }

  override def deactivate(): Unit = synchronized {
    if (currentWebSocketSubscription.nonEmpty) {
      verbose(l"Deactivate WebSocket process.")
      currentWebSocketSubscription.foreach(_.destroy())
      currentWebSocketSubscription = None
      connected ! false
    }
  }

  private var currentWebSocketSubscription = Option.empty[Subscription]

  private val retryCount = new AtomicInteger(0)

  private def webSocketProcessEngine(initialDelay: FiniteDuration): Subscription = {
    verbose(l"Constructing WebSocket engine subscription.")

    val request = for {
      _     <- CancellableFuture.delay(initialDelay).future
      _     <- networkModeService.isOnline.onTrue
      _     =  info(l"Opening WebSocket... ${showString({if (retryCount.get() == 0) "" else s"Retry count: ${retryCount.get()}"})}")
      token <- accessTokenProvider.currentToken()
      resp  <- token match {
                 case Left(errorResponse) => Future.failed(errorResponse)
                 case Right(token) => Future.successful(requestCreator(token))
               }
    } yield resp

    request.onFailure { case errorResponse =>
      error(l"Error while opening WebSocket: $errorResponse")
      deactivate()
      activate(initialDelay = backoff.delay(retryCount.incrementAndGet()))
    }

    EventStream.from(request, dispatcher).flatMap(webSocketFactory.openWebSocket).on(dispatcher) {
      case SocketEvent.Opened(_) =>
        verbose(l"WebSocket opened")
        connected ! true
        retryCount.set(0)
      case SocketEvent.Closing(socket, _, _) =>
        verbose(l"WebSocket closing")
        //ignore close code and reason. just close socket with normal code
        socket.close(WebSocket.CloseCodes.NormalClosure)
        retryCount.set(0)
        deactivate()
      case SocketEvent.Closed(_, Some(errorResponse)) =>
        error(l"WebSocket closed with error: $errorResponse")
        deactivate()
        activate(initialDelay = backoff.delay(retryCount.incrementAndGet()))
      case SocketEvent.Closed(_, _) =>
        verbose(l"WebSocket closed")
        deactivate()
        activate(initialDelay = backoff.delay(retryCount.incrementAndGet()))
      case SocketEvent.Message(_, NotificationsResponseEncoded(notifs @ _*)) =>
        verbose(l"WebSocket notifications received (${notifs.length})")
        notifications ! notifs
      case event =>
        error(l"Unknown WebSocket event received: $event")
    }
  }

}
