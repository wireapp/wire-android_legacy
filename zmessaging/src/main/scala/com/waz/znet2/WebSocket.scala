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

object WebSocket {

  /**
    * According to RFC 6455
    */
  object CloseCodes {

    /**
      * Indicates a normal closure, meaning that the purpose for
      * which the connection was established has been fulfilled.
      */
    val NormalClosure = 1000

  }

}

trait WebSocket {
  def close(code: Int, reason: Option[String] = None): Boolean
  def cancel(): Unit
}

class OkHttpWebSocket(val socket: okhttp3.WebSocket) extends WebSocket {
  override def close(code: Int, reason: Option[String]): Boolean = socket.close(code, reason.orNull)
  override def cancel(): Unit = socket.cancel()
}


