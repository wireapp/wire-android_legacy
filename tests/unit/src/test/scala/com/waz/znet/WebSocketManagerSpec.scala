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
package com.waz.znet

import java.net.InetSocketAddress
import java.util.concurrent
import java.util.concurrent.{CountDownLatch, TimeUnit}

import android.net.Uri
import com.waz.RobolectricUtils
import com.waz.api.EmailCredentials
import com.waz.model.UserId
import com.waz.testutils.Slow
import com.waz.utils.{ExponentialBackoff, returning}
import com.waz.znet.AuthenticationManager.AccessToken
import com.waz.znet.ZNetClient.ErrorOr
import org.java_websocket.WebSocket
import org.java_websocket.framing.Framedata
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.robolectric.shadows.ShadowLog
import org.scalatest.{BeforeAndAfter, FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try


@Ignore class WebSocketManagerSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils {

  import com.waz.threading.Threading.Implicits.Background
  import com.waz.utils.events.EventContext.Implicits.global

  ShadowLog.stream = System.out

  object lock
  var socket: Option[WebSocket] = None
  var server: TestServer = _
  var manager: WebSocketClient = _
  var connectionCount = 0

  var authResult: ErrorOr[AccessToken] = Future.successful(Right(AccessToken("test_token", "Bearer")))
  val auth = new AccessTokenProvider {
    override def currentToken() = authResult
    override def onPasswordReset(emailCredentials: Option[EmailCredentials]) = Future.successful(Right({}))
  }

  val port = 9982
  def createServer() = new TestServer(port)
  def createClient(pongTimeout: FiniteDuration = 15.seconds, backoff: ExponentialBackoff = WebSocketClient.defaultBackoff) = {
    returning(new WebSocketClient(context, UserId(), new AsyncClientImpl(), Uri.parse(s"http://localhost:$port"), auth, pongTimeout = pongTimeout, backoff = backoff)) {_.connected.disableAutowiring()}
  }

  before {
    server = createServer()
    server.start()
    connectionCount = 0
  }

  after {
    server.stop()
    if (manager != null)
      Await.ready(manager.close(), 10.seconds)
  }

  feature("Connection") {

    scenario("Connect to server and close") {
      manager = createClient()
      awaitUi(socket.isDefined)
      assertClientConnected(true)
      manager.close()
      Thread.sleep(100)
      assertClientConnected(false)
      awaitUi(socket.isEmpty)
    }

    scenario("Retry connection to unavailable server", Slow) {
      server.stop()

      manager = createClient()
      Thread.sleep(1000)
      socket.isDefined shouldEqual false
      assertClientConnected(false)

      server = createServer()
      server.start()
      awaitUi(socket.isDefined)(10.seconds)
      assertClientConnected(true)

      manager.close()
      awaitUi(socket.isEmpty)(10.seconds)
    }

    scenario("Retry on closed connection", Slow) {
      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)
      val ws = socket.get
      socket = None
      ws.close()
      awaitUi(socket.isDefined)(10.seconds)
      manager.close()
      awaitUi(socket.isEmpty)(10.seconds)
    }

    scenario("Retry when server stops working", Slow) {
      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)
      assertClientConnected(true)

      server.stop()
      socket = None

      Thread.sleep(1000)
      assertClientConnected(false)

      server = createServer()
      server.start()
      awaitUi(socket.isDefined)(10.seconds)
      assertClientConnected(true)

      manager.close()
      awaitUi(socket.isEmpty)(10.seconds)
    }

    scenario("Ensure that connection is actually closed from server when client calls close") {

      def test(i: Int) = {
        manager = createClient()
        awaitUi(socket.isDefined)(10.seconds)
        Await.ready(manager.close(), 10.seconds)
        awaitUi(socket.isEmpty)(10.seconds)
        server.sendMessage("Uh oh") shouldBe false
      }

      (1 to 10) foreach test
    }


  }

  feature("Ping") {

    def pingToBoolean = manager.pingPong().map(_ => true).recover{case _ => false}

    scenario("Simple ping pong should succeed") {
      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)
      Await.result(pingToBoolean, 10.seconds) shouldEqual true
    }

    scenario("Multiple pings should be discarded after the first one, taking on its result") {
      server.pongDelay = 500.millis

      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)

      val futures = (1 to 3) map (_ => pingToBoolean)

      Await.result(Future.sequence(futures.map(_.future)).map(_.forall(v => v)), 10.seconds) shouldEqual true

      server.pingCount shouldEqual 1
    }

    scenario("Server fails to send pong should fail ping") {
      server.returnPing = false
      manager = createClient(1.second)
      awaitUi(socket.isDefined)(10.seconds)
      Await.result(pingToBoolean, 10.seconds) shouldEqual false
    }

    scenario("Schedule recurring ping") {
      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)
      manager.scheduleRecurringPing(100.millis)
      Thread.sleep(500)
      server.pingCount should be >= 4
    }

    scenario("Cancelling ping schedules") {
      manager = createClient()
      awaitUi(socket.isDefined)(10.seconds)
      manager.scheduleRecurringPing(200.millis)
      Thread.sleep(500)
      server.pingCount should be >= 2

      manager.scheduleRecurringPing(100.millis)
      Thread.sleep(500)

      server.pingCount should be >= 6
    }

    //Basically test that the pongPromise is deleted if the ping times-out, or else all future pongs will fail!
    scenario("Failed verification should reset pong timeout") {
      server.returnPing = false
      manager = createClient(100.millis, new ExponentialBackoff(1.millis, 1.millis)) //very small backoff to make tests faster
      awaitUi(socket.isDefined)(10.seconds)

      manager.verifyConnection()

      Thread.sleep(500)

      connectionCount should be <= 5
    }

    scenario("Ping pong callbacks") {
      manager = createClient(pongTimeout = 100.millis)
      awaitUi(socket.isDefined)(10.seconds)

      val pingLatch = new CountDownLatch(4)
      val pongLatch = new CountDownLatch(3)

      manager.onPing { _ =>
        pingLatch.countDown()
      }

      manager.onPong { _ =>
        pongLatch.countDown()
      }

      val seqPings = (1 to 4).foldLeft(Future.successful(())) { (f, count) =>
        f.flatMap { _ =>
          if (count == 4) {
            server.returnPing = false
          }
          manager.pingPong().future
        }
      }

      pingLatch.await(3.seconds.toMillis, TimeUnit.SECONDS)
      pongLatch.await(3.seconds.toMillis, TimeUnit.SECONDS)

    }
  }

  def assertClientConnected(connected: Boolean) = {
    val latch = new concurrent.CountDownLatch(1)
    manager.connected { st =>
      println(s"Client connected?: $st")
      if (st == connected) latch.countDown()
    }
    latch.await(3000, TimeUnit.MILLISECONDS) shouldEqual true
  }


  class TestServer(port: Int) extends WebSocketServer(new InetSocketAddress(port)) {

    var pongDelay = Duration.Zero
    var pingCount = 0
    var returnPing = true

    override def onOpen(conn: WebSocket, handshake: ClientHandshake): Unit = {
      println("server: onOpen")
      socket = Some(conn)
      connectionCount += 1
      lock.synchronized(lock.notifyAll())
    }

    override def onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean): Unit = {
      println("server: onClose")
      if (socket.contains(conn)) {
        socket = None
        lock.synchronized(lock.notifyAll())
      }
    }

    override def onMessage(conn: WebSocket, message: String): Unit = {
      println(s"Server received a message: $message")
    }

    override def onWebsocketPing(conn: WebSocket, f: Framedata): Unit = {
      println(s"Server received ping: ${new String(f.getPayloadData.array(), "utf8")}")
      Thread.sleep(pongDelay.toMillis)
      pingCount += 1
      if (returnPing) super.onWebsocketPing(conn, f)
    }

    override def onError(conn: WebSocket, ex: Exception): Unit = {
      println(s"Server got an error $ex")
      ex.printStackTrace(Console.err)
    }

    def sendMessage(msg: String): Boolean = {
      Try(socket.map{ s => s.send(msg); true}).toOption.flatten.getOrElse(false)
    }
  }

}
