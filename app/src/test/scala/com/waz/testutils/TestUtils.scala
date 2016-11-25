/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.testutils

import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.WireContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object TestUtils {

  implicit val eventContext = EventContext.Implicits.global
  implicit val executionContext = ExecutionContext.Implicits.global
  val timeout = 1000;

  def signalTest[A](signal: Signal[A])(test: A => Boolean)(trigger: => Unit)(implicit printVals: PrintSignalVals): Unit = {
    signal.disableAutowiring()
    trigger
    if (printVals.debug) println("****")
    Await.result(signal.filter { value =>
      if (printVals.debug) println(value)
      test(value)
    }.head, Duration(timeout, TimeUnit.MILLISECONDS))
    if (printVals.debug) println("****")
  }

  case class PrintSignalVals(debug: Boolean)

  implicit class RichLatch(latch: CountDownLatch) {
    def waitDuration(implicit duration: Duration): Unit = latch.await(duration.toMillis, TimeUnit.MILLISECONDS)
  }

}


abstract class TestWireContext extends WireContext


