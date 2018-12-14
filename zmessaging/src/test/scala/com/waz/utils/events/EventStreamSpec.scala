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
package com.waz.utils.events

import com.waz.specs.ZSpec
import com.waz.utils.returning

class EventStreamSpec extends ZSpec {

  import EventContext.Implicits.global

  feature("FlatMapLatest") {

    scenario("unsubscribe from source and current mapped signal on onUnwire") {
      val a: SourceStream[Int] = EventStream()
      val b: SourceStream[Int] = EventStream()

      val subscription = a.flatMap(_ => b) { _ => }
      a ! 1

      withClue("mapped event stream should have subscriber after element emitting from source event stream.") {
        b.hasSubscribers shouldBe true
      }

      subscription.unsubscribe()

      withClue("source event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream") {
        a.hasSubscribers shouldBe false
      }

      withClue("mapped event stream should have no subscribers after onUnwire was called on FlatMapLatestEventStream") {
        b.hasSubscribers shouldBe false
      }
    }

    scenario("discard old mapped event stream when new element emitted from source event stream") {
      val a: SourceStream[String] = EventStream()
      val b: SourceStream[String] = EventStream()
      val c: SourceStream[String] = EventStream()

      var flatMapCalledCount = 0
      var lastReceivedElement: Option[String] = None
      val subscription = a.flatMap { _ =>
        returning(if (flatMapCalledCount == 0) b else c) { _ => flatMapCalledCount += 1 }
      } { elem => lastReceivedElement = Some(elem) }

      a ! "a"

      withClue("mapped event stream 'b' should have subscriber after first element emitting from source event stream.") {
        b.hasSubscribers shouldBe true
      }

      b ! "b"

      withClue("flatMapLatest event stream should provide events emitted from mapped signal 'b'") {
        lastReceivedElement shouldBe Some("b")
      }

      a ! "a"

      withClue("mapped event stream 'b' should have no subscribers after second element emitting from source event stream.") {
        b.hasSubscribers shouldBe false
      }

      withClue("mapped event stream 'c' should have subscriber after second element emitting from source event stream.") {
        c.hasSubscribers shouldBe true
      }

      c ! "c"
      b ! "b"

      withClue("flatMapLatest event stream should provide events emitted from mapped signal 'c'") {
        lastReceivedElement shouldBe Some("c")
      }

      subscription.unsubscribe()
    }

  }

}
