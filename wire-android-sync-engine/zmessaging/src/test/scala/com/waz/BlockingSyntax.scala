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
package com.waz

import java.util.concurrent.TimeoutException

import com.waz.utils.events.{EventContext, EventStream, Subscription}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, _}

object BlockingSyntax {

  def toBlocking[T,R](stream: EventStream[T])
                     (doWithBlocking: BlockingEventStream[T] => R)
                     (implicit ec: EventContext = EventContext.Global): R = {
    val blocking = new BlockingEventStream[T](stream)
    blocking.subscribe
    val result = doWithBlocking(blocking)
    blocking.unsubscribe()
    result
  }

  class BlockingEventStream[T](private val eventStream: EventStream[T]) {
    private var subscription: Subscription = _
    private val events = ArrayBuffer.empty[T]

    private val waitingStepInMillis = 100
    private val defaultTimeout: Duration = 3.seconds

    def subscribe(implicit ev: EventContext): Unit = {
      subscription = eventStream { e =>
        println(s"BlockingEventStream. Received event: $e")
        events.append(e)
      }
    }

    def unsubscribe(): Unit = {
      if (subscription != null) subscription.destroy()
    }

    private def waitForEvents(eventsCount: Int, timeout: Duration = defaultTimeout): List[T] = {
      var alreadyWaiting = 0
      while (events.size < eventsCount) {
        Thread.sleep(waitingStepInMillis)
        alreadyWaiting += waitingStepInMillis
        if (alreadyWaiting >= timeout.toMillis) throw new TimeoutException()
      }
      events.toList
    }

    def takeEvents(count: Int, timeout: Duration = defaultTimeout): List[T] = {
      waitForEvents(count, timeout)
      events.take(count).toList
    }

    def waitForEvents(duration: Duration): List[T] = {
      val eventsCount = events.size
      Thread.sleep(duration.toMillis)
      if (events.size == eventsCount) List.empty[T]
      else events.drop(eventsCount).toList
    }

    def getEvent(index: Int, timeout: Duration = defaultTimeout): T = {
      waitForEvents(index + 1, timeout)
      events(index)
    }

  }

}
