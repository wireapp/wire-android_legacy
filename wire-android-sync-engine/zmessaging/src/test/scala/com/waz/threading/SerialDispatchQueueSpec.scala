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
package com.waz.threading

import java.lang.System.nanoTime
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

import com.waz.specs.AndroidFreeSpec

import scala.concurrent.duration.Duration.Zero
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SerialDispatchQueueSpec extends AndroidFreeSpec {

  scenario("Execute multiple tasks with no delay") {
    Await.result(executeMultiple(100, Zero, 100.millis), 10.seconds)
  }

  scenario("Execute multiple tasks with small delay") {
    Await.result(executeMultiple(100, 40.millis, 100.millis), 10.seconds)
  }

  scenario("Execute multiple tasks with bigger delay") {
    Await.result(executeMultiple(100, 100.millis, 100.millis), 10.seconds)
  }

  scenario("Execute a lot of tasks with no delay") {
    Await.result(executeMultiple(1000, Zero, 1.micro), 30.seconds)
  }

  def executeMultiple(count: Int, delay: FiniteDuration, innerDelay: FiniteDuration) = {
    val counter = new AtomicInteger(0)
    implicit val dispatcher: SerialDispatchQueue = new SerialDispatchQueue()

    Future.sequence(Seq.fill(count) {
      sleepRandom(delay)
      Future {
        sleepRandom(innerDelay / 2)
        val count = counter.incrementAndGet
        if (count > 1) fail("counter was greater than one, that means tasks were executed in parallel instead of serially")
        sleepRandom(innerDelay / 2)
        counter.decrementAndGet()
      }
    })
  }

  def sleepRandom(delay: FiniteDuration) = if (delay > Zero) {
    val d = nextLong(delay.toNanos)
    Thread.sleep(d / 1000000L, (d % 1000000L).toInt)
  }

  private val local = new ThreadLocal[Random] {
    override protected def initialValue = new Random(nanoTime)
  }
  def nextLong(l: Long): Long = (math.abs(local.get.nextDouble) * l).toLong
}
