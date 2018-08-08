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

import com.waz.ZLog.{info, warn}
import com.waz.sync.client.{JsonObjectResponse, ResponseContent, StringResponse, BinaryResponse}
import com.waz.utils.events.EventStream
import com.waz.znet2.WebSocketFactory.SocketEvent
import com.waz.znet2.http.{Body, Request}
import okio.ByteString
import org.json.JSONObject

import scala.util.Try

object WebSocketFactory {

  sealed trait SocketEvent
  object SocketEvent {
    case class Opened(socket: WebSocket) extends SocketEvent
    case class Message(socket: WebSocket, content: ResponseContent) extends SocketEvent
    case class Closing(webSocket: WebSocket, code: Int, reason: String) extends SocketEvent
    case class Closed(socket: WebSocket, error: Option[Throwable] = None) extends SocketEvent
  }

}

trait WebSocketFactory {
  def openWebSocket(request: Request[Body]): EventStream[SocketEvent]
}

object OkHttpWebSocketFactory extends WebSocketFactory {
  import com.waz.ZLog.ImplicitTag._
  import okhttp3.{
    OkHttpClient,
    WebSocketListener,
    Headers => OkHeaders,
    Request => OkRequest,
    Response => OkResponse,
    WebSocket => OkWebSocket
  }
  import HttpClientOkHttpImpl.convertHttpRequest

  //TODO Should be created somewhere outside
  private lazy val okHttpClient = new OkHttpClient()

  override def openWebSocket(request: Request[Body]): EventStream[SocketEvent] = {
    new EventStream[SocketEvent] {

      @volatile private var socket: Option[OkWebSocket] = None

      override protected def onWire(): Unit = {
        val socket = okHttpClient.newWebSocket(convertHttpRequest(request), new WebSocketListener {

          override def onOpen(webSocket: OkWebSocket, response: OkResponse): Unit = {
            info("WebSocket connection has been opened")
            publish(SocketEvent.Opened(new OkHttpWebSocket(webSocket)))
          }

          override def onMessage(webSocket: OkWebSocket, text: String): Unit = {
            info("WebSocket received a text message.")
            val content = Try(JsonObjectResponse(new JSONObject(text))).getOrElse(StringResponse(text))
            publish(SocketEvent.Message(new OkHttpWebSocket(webSocket), content))
          }

          override def onMessage(webSocket: OkWebSocket, bytes: ByteString): Unit = {
            info("WebSocket received a bytes message.")
            val content = Try(JsonObjectResponse(new JSONObject(bytes.utf8()))).getOrElse(BinaryResponse(bytes.toByteArray, ""))
            publish(SocketEvent.Message(new OkHttpWebSocket(webSocket), content))
          }

          override def onClosing(webSocket: OkWebSocket, code: Int, reason: String): Unit = {
            info("WebSocket connection is going to be closed")
            publish(SocketEvent.Closing(new OkHttpWebSocket(webSocket), code, reason))
          }

          override def onClosed(webSocket: OkWebSocket, code: Int, reason: String): Unit = {
            info("WebSocket connection has been closed")
            publish(SocketEvent.Closed(new OkHttpWebSocket(webSocket)))
          }

          override def onFailure(webSocket: OkWebSocket, ex: Throwable, response: okhttp3.Response): Unit = {
            warn(s"WebSocket connection has been failed: ${ex.getMessage}")
            publish(SocketEvent.Closed(new OkHttpWebSocket(webSocket), Some(ex)))
          }
        })

        this.socket = Some(socket)
      }

      override protected def onUnwire(): Unit = {
        info("Cancelling websocket.")
        socket.foreach(_.cancel())
      }
    }
  }

}
