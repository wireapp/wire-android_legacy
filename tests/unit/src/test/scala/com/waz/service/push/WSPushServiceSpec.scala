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

import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.model.{Uid, UserId}
import com.waz.service.push.WSPushServiceImpl.RequestCreator
import com.waz.specs.ZMockSpec
import com.waz.sync.client.AuthenticationManager.AccessToken
import com.waz.sync.client.{AccessTokenProvider, JsonObjectResponse, PushNotificationEncoded}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.{Backoff, ExponentialBackoff}
import com.waz.znet2.WebSocketFactory.SocketEvent
import com.waz.znet2._
import com.waz.znet2.http.{Body, EmptyBodyImpl, Method, Request}
import org.json.{JSONArray, JSONObject}
import org.scalatest.Ignore

import scala.concurrent.Future
import scala.concurrent.duration._

@Ignore
class WSPushServiceSpec extends ZMockSpec {

  import com.waz.utils.events.EventContext.Implicits.global

  private val accessTokenProvider = mock[AccessTokenProvider]
  private val webSocketFactory = mock[WebSocketFactory]
  private val webSocket = mock[WebSocket]

  private val accessTokenSuccess = Future.successful(Right(AccessToken("token", "type")))
  private val accessTokenError = Future.successful(Left(ErrorResponse.InternalError))
  private val httpRequest = Request.create[Body](method = Method.Post, new URL("http://www.test.com"), body = EmptyBodyImpl)

  private val fakeWebSocketEvents: SourceStream[SocketEvent] = EventStream()

  private def createWSPushService(userId:              UserId = UserId("userId"),
                                  accessTokenProvider: AccessTokenProvider = accessTokenProvider,
                                  requestCreator:      RequestCreator = _ => httpRequest,
                                  webSocketFactory:    WebSocketFactory = webSocketFactory,
                                  backoff:             Backoff = ExponentialBackoff.constantBackof(100.millis)) = {
    new WSPushServiceImpl(userId, accessTokenProvider, requestCreator, webSocketFactory, backoff)
  }

  feature("WSPushService") {

    scenario("On activation should get access token and become connected. On deactivation become disconnected immediately.") {
      (accessTokenProvider.currentToken _).expects().once().returning(accessTokenSuccess)
      (webSocketFactory.openWebSocket _).expects(httpRequest).once().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(1000)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      noException shouldBe thrownBy { await(service.connected.ifTrue.head) }
      service.deactivate()

      service.connected.currentValue shouldBe Some(false)
    }

    scenario("When can not get an access token should retry to connect.") {
      val accessTokenResults = List(accessTokenError, accessTokenSuccess).toIterator
      (accessTokenProvider.currentToken _).expects().twice().onCall(() => accessTokenResults.next())
      (webSocketFactory.openWebSocket _).expects(httpRequest).once().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(1000)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      noException shouldBe thrownBy { await(service.connected.ifTrue.head) }
    }

    scenario("When web socket closed should become unconnected and retry to connect.") {
      (accessTokenProvider.currentToken _).expects().twice().returning(accessTokenSuccess)
      (webSocketFactory.openWebSocket _).expects(httpRequest).twice().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Closed(webSocket, Some(new InterruptedException))

      noException shouldBe thrownBy { await(service.connected.ifFalse.head) }

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      noException shouldBe thrownBy { await(service.connected.ifTrue.head) }
    }

    scenario("When web socket is going to be closed by other side should close web socket with normal closure code.") {
      (accessTokenProvider.currentToken _).expects().once().returning(accessTokenSuccess)
      (webSocketFactory.openWebSocket _).expects(httpRequest).once().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      (webSocket.close _).expects(WebSocket.CloseCodes.NormalClosure, *).once().returning(true)

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Closing(webSocket, 0, null)

      Thread.sleep(100)
    }

    scenario("When web socket emmit message of push notifications, should push it to notifications stream and stay connected.") {
      (accessTokenProvider.currentToken _).expects().once().returning(accessTokenSuccess)
      (webSocketFactory.openWebSocket _).expects(httpRequest).once().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      val notification = PushNotificationEncoded(id = Uid(), new JSONArray)
      import com.waz.utils.Json.syntax._
      val responseContent = JsonObjectResponse(notification.toJson)

      var gotNotification = false
      service.notifications { _ =>
        gotNotification = true
      }

      fakeWebSocketEvents ! SocketEvent.Message(webSocket, responseContent)

      gotNotification shouldBe true

      noException shouldBe thrownBy { await(service.connected.ifTrue.head) }
    }

    scenario("When web socket emmit unknown message, should ignore it and stay connected.") {
      (accessTokenProvider.currentToken _).expects().once().returning(accessTokenSuccess)
      (webSocketFactory.openWebSocket _).expects(httpRequest).once().returning(fakeWebSocketEvents)

      val service = createWSPushService()
      service.activate()

      Thread.sleep(500)
      fakeWebSocketEvents ! SocketEvent.Opened(webSocket)

      val responseContent = JsonObjectResponse(new JSONObject())

      var gotNotification = false
      service.notifications { _ =>
        gotNotification = true
      }

      fakeWebSocketEvents ! SocketEvent.Message(webSocket, responseContent)

      gotNotification shouldBe false

      noException shouldBe thrownBy { await(service.connected.ifTrue.head) }
    }

    scenario("When connect and disconnect called to often, should stay in the correct state.") {
      (accessTokenProvider.currentToken _).expects().returning(accessTokenSuccess).anyNumberOfTimes()
      (webSocketFactory.openWebSocket _).expects(httpRequest).returning(fakeWebSocketEvents).anyNumberOfTimes()

      val service = createWSPushService()
      service.activate()
      (1 until 20).foreach { i =>
        Thread.sleep(50)
        if (i%2 == 0) service.deactivate() else service.activate()
      }

      noException shouldBe thrownBy { await(service.connected.ifFalse.head) }
    }

  }

}
