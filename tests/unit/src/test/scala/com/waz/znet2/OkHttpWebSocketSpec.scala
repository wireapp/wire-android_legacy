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
package com.waz.znet2

import java.net.URL

import com.waz.utils.events.EventContext
import com.waz.znet2
import com.waz.znet2.WebSocketFactory.SocketEvent
import com.waz.znet2.http.{Body, Method, Request}
import io.fabric8.mockwebserver.DefaultMockServer
import org.scalatest.{BeforeAndAfterEach, Inside, MustMatchers, WordSpec}

import scala.concurrent.duration._
import scala.util.Try

class OkHttpWebSocketSpec extends WordSpec with MustMatchers with Inside with BeforeAndAfterEach {

  import EventContext.Implicits.global
  import com.waz.BlockingSyntax.toBlocking

  private val testPath = "/test"
  private val defaultWaiting = 100
  private def testWebSocketRequest(url: String): Request[Body] = Request.create(method = Method.Get, url = new URL(url))

  private var mockServer: DefaultMockServer = _

  override protected def beforeEach(): Unit = {
    mockServer = new DefaultMockServer()
    mockServer.start()
  }

  override protected def afterEach(): Unit = {
    mockServer.shutdown()
  }

  "OkHttp events stream" should {

    "provide all okHttp events properly when socket closed without error." in {
      val textMessage = "Text message"
      val bytesMessage = Array[Byte](1, 2, 3, 4)

      mockServer.expect().get().withPath(testPath)
        .andUpgradeToWebSocket()
        .open()
        .waitFor(defaultWaiting).andEmit(textMessage)
        .waitFor(defaultWaiting).andEmit(bytesMessage)
        .done().once()


      toBlocking(znet2.OkHttpWebSocketFactory.openWebSocket(testWebSocketRequest(mockServer.url(testPath)))) { stream =>
        val firstEvent :: secondEvent :: thirdEvent :: fourthEvent :: Nil = stream.takeEvents(4)

        firstEvent mustBe an[SocketEvent.Opened]
        secondEvent mustBe an[SocketEvent.Message]
        thirdEvent mustBe an[SocketEvent.Message]
        fourthEvent mustBe an[SocketEvent.Closing]

        withClue("No events should be emitted after socket has been closed") {
          stream.waitForEvents(2.seconds) mustBe List.empty[SocketEvent]
        }
      }
    }

    "provide all okHttp events properly when socket closed with error." in {
      mockServer.expect().get().withPath(testPath)
        .andUpgradeToWebSocket()
        .open()
        .waitFor(10000).andEmit("")
        .done().once()

      toBlocking(znet2.OkHttpWebSocketFactory.openWebSocket(testWebSocketRequest(mockServer.url(testPath)))) { stream =>
        val firstEvent = stream.getEvent(0)
        Try { mockServer.shutdown() } //we do not care about this error
      val secondEvent = stream.getEvent(1)

        firstEvent mustBe an[SocketEvent.Opened]
        secondEvent mustBe an[SocketEvent.Closed]

        inside(secondEvent) { case SocketEvent.Closed(_, error) =>
          error mustBe an[Some[_]]
        }

        withClue("No events should be emitted after socket has been closed") {
          stream.waitForEvents(2.seconds) mustBe List.empty[SocketEvent]
        }
      }
    }
  }




}

