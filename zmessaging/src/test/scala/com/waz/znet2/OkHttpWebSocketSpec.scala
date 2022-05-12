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

import com.wire.signals.EventContext
import com.waz.znet2
import com.waz.znet2.WebSocketFactory.SocketEvent
import com.waz.znet2.http.{Body, Method, Request}
import org.scalatest.{BeforeAndAfterEach, Inside, MustMatchers, WordSpec, BeforeAndAfterAll}

import scala.concurrent.duration._
import scala.util.Try

// imports for akka-http's websocket server
import akka.NotUsed
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Source, Sink, Keep }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ TextMessage, Message, BinaryMessage }
import akka.http.scaladsl.server.Directives
import scala.concurrent.{ Future, Promise, Await }
import scala.concurrent.duration._

class OkHttpWebSocketSpec extends WordSpec with MustMatchers with Inside with BeforeAndAfterEach {
  import com.waz.BlockingSyntax.toBlocking

  private var wsPort: Int = _
  private def testPath(): String = s"http://localhost:${wsPort}/test"
  private def testWebSocketRequest(url: String): Request[Body] = Request.create(method = Method.Get, url = new URL(url))

  import Directives._

  class WSServer {
    private var bindingFuture: Future[Http.ServerBinding] = _
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    def start[A](flow: Flow[Message, Message, A]): Unit = {
      val route =
        path("test") {
          get {
            handleWebSocketMessages(flow)
          }
        }

      // binding to port 0 means the OS can choose a free port
      bindingFuture = Http().bindAndHandle(route, "localhost", 0)
      wsPort = Await.result(bindingFuture, 5.seconds).localAddress.getPort()
    }

    def stop(): Unit = {
      import system.dispatcher // executionContext for the future transformations
      bindingFuture
          .flatMap(_.unbind()) // trigger unbinding from the port
          .onComplete(_ => system.terminate()) // and shutdown when done
    }

  }

  private var wsServer: WSServer = _

  override protected def beforeEach(): Unit = {
    wsServer = new WSServer()
  }
  override protected def afterEach(): Unit = {
    wsServer.stop()
  }

  "OkHttp events stream" should {

    "provide all okHttp events properly when socket closed without error." in {
      val textMessage = "Text message"
      val bytesMessage = ByteString(1, 2, 3, 4)

      // -- set up websocket server --
      // emit two messages and then close the connection
      val flowTwoMessageClose: Flow[Message, Message, NotUsed] =
          Flow.fromSinkAndSource(
            Sink.foreach[Message](println),
            Source(List(TextMessage(textMessage), BinaryMessage(bytesMessage)))
          )
      wsServer.start(flowTwoMessageClose)

      // -- connect and assert --
      toBlocking(new znet2.OkHttpWebSocketFactory(None).openWebSocket(testWebSocketRequest(testPath))) { stream =>
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
      // -- set up websocket server --
      // emit nothing but keep the connection open
      val flowWait: Flow[Message, Message, Promise[Option[Message]]] =
        Flow.fromSinkAndSourceMat(
          Sink.foreach[Message](println),
          Source.empty
            .concatMat(Source.maybe[Message])(Keep.right))(Keep.right)
      wsServer.start(flowWait)

      // -- connect and assert --
      toBlocking(new znet2.OkHttpWebSocketFactory(None).openWebSocket(testWebSocketRequest(testPath))) { stream =>
        val firstEvent = stream.getEvent(0)
        Try { wsServer.stop() } //we do not care about this error
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
