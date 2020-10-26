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
package com.waz.service

import scala.concurrent.duration._

/** This class enables us to shorten timeouts in testing.
  */
class Timeouts {
  val search: GraphSearch = new GraphSearch
  val typing = new Typing
  val calling = new Calling
  val webSocket = new WebSocket
  val messages = new Messages
  val notifications = new Notifications

  class Messages {
    def lastReadPostDelay = 15.seconds
    def incomingTimeout = 30.seconds
  }

  class GraphSearch {
    def cacheExpiryTime = 7.days // time after which the cache expires (results are considered invalid, and will be ignored)
    def localSearchDelay = 1.second // time after which we will return local search results if no result is available from backend
    def topPeopleMessageInterval = 30.days //top people will be sorted by the number of messages exchanged since this time
  }

  class Typing {
    def stopTimeout = 1.minute // design spec says 3 seconds, Jon says never but agrees to a minute...
    def refreshDelay = 45.seconds // resend is typing event every 45 seconds if user is still typing
    def receiverTimeout = 60.seconds // safety timeout for when we don't receive a notification that typing has stopped
  }

  class Calling {
    def callConnectingTimeout = 1.minute
  }

  class WebSocket {
    def inactivityTimeout = 3.seconds
    def connectionTimeout = 8.seconds
  }

  class Notifications {
    def clearThrottling = 3.seconds
  }
}
