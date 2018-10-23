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

import com.waz.RobolectricUtils
import org.scalatest._

@Ignore class EventStreamWithAuxSignalSpec extends FeatureSpec with Matchers with OptionValues with RobolectricTests with RobolectricUtils {

  lazy val e = Publisher[String](None)
  lazy val aux = new SourceSignal[Int]

  lazy val r = new EventStreamWithAuxSignal(e, aux)

  private var events = List.empty[(String, Option[Int])]

  scenario("Subscribe, send stuff, unsubscribe, send more stuff") {

    val sub = r { r =>
      events = r :: events
    } (EventContext.Global)

    events shouldEqual Nil

    e ! "meep"
    events shouldEqual List(("meep", None))

    aux ! 1
    events shouldEqual List(("meep", None))

    e ! "foo"
    events shouldEqual List(("foo", Some(1)), ("meep", None))

    e ! "meep"
    events shouldEqual List(("meep", Some(1)), ("foo", Some(1)), ("meep", None))

    aux ! 2
    events should have size 3

    e ! "meep"
    events should have size 4
    events.head shouldEqual ("meep", Some(2))

    sub.unsubscribe()

    e ! "foo"
    aux ! 3

    events should have size 4
  }
}
