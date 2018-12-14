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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import com.waz.specs.AndroidFreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class DispatchQueueSpec extends AndroidFreeSpec {

  feature("Serial execution dispatch queue - concurrentTasks = 1") {

    scenario("Execute multiple Runnable in order, without concurrent overlap") {
      val queue = new SerialDispatchQueue(Threading.Background, name = "DispatchQueueSpec1")

      @volatile var source = 0
      @volatile var dest = 0
      val latch = new CountDownLatch(1)

      queue {
        for (i <- 1 to 100) source = i
      }

      queue {
        dest = source
      }

      queue {
        source = 0
        latch.countDown()
      }

      latch.await()

      source should be(0)
      dest should be(100)
    }

    scenario("Cancel not yet started task") {
      val queue = new SerialDispatchQueue(Threading.Background, name = "DispatchQueueSpec2")

      @volatile var count = 0
      val latch = new CountDownLatch(1)

      queue {
        latch.await()
      }

      val f = queue {
        count = 1
      }

      f.cancel()("")
      latch.countDown()

      Thread.sleep(100) //make sure second task could run if it wasn't properly cancelled

      count shouldEqual 0
    }

    scenario("Log error") {
      implicit val queue = new SerialDispatchQueue(Threading.Background, name = "DispatchQueueSpec3")

      intercept[Exception] {
        Await.result(queue { throw new Error("Some error") }, 1.second)
      }

      intercept[Exception] {
        Await.result(CancellableFuture.successful(true) map { _ => throw new Error("Delayed error")}, 1.second)
      }

      intercept[Exception] {
        Await.result(CancellableFuture.delay(10.millis) map { _ => throw new Error("Delayed error") }, 1.second)
      }

      intercept[Exception] {
        Await.result(CancellableFuture.delayed(10.millis) { throw new Error("Delayed error") }, 1.second)
      }
    }
  }

  feature("Limited concurrency dispatch queue") {

    scenario("Execute two concurrent tasks, but no more") {
      val queue = new LimitedDispatchQueue(2, Threading.Background) // make sure we have enough threads in a pool

      val finishLatch = new CountDownLatch(10)
      val count = new AtomicInteger(0)
      val max = new AtomicInteger(0)
      for (_ <- 1 to 10) {
        queue {
          val c = count.incrementAndGet()
          var done = false
          while (!done) {
            val m = max.get()
            if (m >= c) done = true
            else done = max.compareAndSet(m, c)
          }
          Thread.sleep(10)
          count.decrementAndGet()
          finishLatch.countDown()
        }
      }

      finishLatch.await()
      max.get shouldEqual 2
    }
  }

  feature("Unlimited dispatch queue") {

    //FIXME - test fails when run in full suite...
    ignore("Execute 3 concurrent tasks") {
      val queue = new UnlimitedDispatchQueue(Threading.Background)

      val latch = new CountDownLatch(3)
      val finishLatch = new CountDownLatch(3)
      @volatile var count = 0

      for (i <- 0 until 3) {
        queue {
          latch.countDown()
          latch.await()
          count += 1
          finishLatch.countDown()
        }
      }

      finishLatch.await()
      count shouldEqual 3
    }
  }
}
