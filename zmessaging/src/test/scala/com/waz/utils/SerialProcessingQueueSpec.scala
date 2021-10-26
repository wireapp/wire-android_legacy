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
package com.waz.utils

import java.util.concurrent.atomic.AtomicInteger

import com.waz.model._
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.DefaultPatienceConfig
import com.waz.zms.BuildConfig
import com.wire.signals.CancellableFuture
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class SerialProcessingQueueSpec extends AndroidFreeSpec with Matchers with ScalaFutures with DefaultPatienceConfig {
  import com.waz.threading.Threading.Implicits.Background

  feature("Grouped event processing queue") {

    scenario("Enqueue events and await results") {
      val domain = if (BuildConfig.FEDERATION_USER_DISCOVERY) Domain("chala.wire.link") else Domain.Empty
      val processedCount = new AtomicInteger(0)
      val queue = new GroupedEventProcessingQueue[ConversationEvent, RConvId](_.convId, {
        case (_, events) =>
          CancellableFuture.delayed(250.millis) { processedCount.addAndGet(events.length) }.future
      })

      val convId = RConvId()
      val future = queue.enqueue(
        Seq(
          TypingEvent(convId, domain, RemoteInstant(Instant.now()), UserId(), domain, isTyping = true),
          TypingEvent(convId, domain, RemoteInstant(Instant.now()), UserId(), domain, isTyping = true),
          TypingEvent(RConvId(), domain, RemoteInstant(Instant.now()), UserId(), domain, isTyping = true)
        )
      )

      Await.result(future, 1.second)
      processedCount.get() shouldEqual 3
    }
  }

  feature("Serialization") {
    val count = new AtomicInteger(0)
    val queue = new SerialProcessingQueue[Int]({ evs => Future successful count.addAndGet(evs.length) })

    scenario("post multiple futures to a queue") {
      val running = new AtomicInteger(0)
      val max = new AtomicInteger(0)

      val tasks = Seq.fill(100) {
        queue.post {
          val c = running.incrementAndGet()
          while (max.get() < c) {
            val v = max.get
            max.compareAndSet(v, math.max(v, c))
          }
          CancellableFuture.delayed(Random.nextInt(50).millis) { running.decrementAndGet() } .future
        }
      }

      Future.sequence(tasks).futureValue

      max.get() shouldEqual 1
    }
  }

  feature("Throttled processing queue") {
    lazy val processedCount = new AtomicInteger(0)
    lazy val queue = new ThrottledProcessingQueue[Int](1.second, { items =>
      CancellableFuture.delayed(250.millis) { processedCount.addAndGet(items.length) } .future
    })

    scenario("process first items immediately") {
      queue.enqueue(Seq(1, 2, 3))
      withDelay( {
        processedCount.get() shouldEqual 3
      }, 500.millis)
    }

    scenario("throttle next processing") {
      queue ! 4
      withDelay({
        processedCount.get() shouldEqual 3
        withDelay {
          processedCount.get() shouldEqual 4
        }
      }, 500.millis)
    }

    scenario("flush queue") {
      queue.enqueue(Seq(5, 6))
      queue.flush()
      withDelay({
        processedCount.get() shouldEqual 6
      }, 2.seconds)

    }

    scenario("throttle following processing") {
      queue ! 7
      withDelay({
        processedCount.get() shouldEqual 6
        withDelay {
          processedCount.get() shouldEqual 7
        }
      }, 500.millis)
    }
  }

  feature("Error handling") {

    scenario("catch and ignore exceptions in processor") {
      var count = 0
      val queue = new SerialProcessingQueue[Int]({ evs =>
        count += evs.length
        throw new Exception("error")
      })

      queue.enqueue(1)
      queue.enqueue(Seq(2, 3, 4))
      queue.enqueue(5)
      val future = queue.enqueue(Seq(6, 7, 8))
      Await.result(future, 1.second)
      count shouldEqual 8
    }

    // @todo : Uncomment and look into why this test is flaky, https://wearezeta.atlassian.net/browse/SQCORE-793
/*    scenario("ignore failures returned from processor") {
      var count = 0
      val queue = new SerialProcessingQueue[Int]({ evs =>
        count += evs.length
        Future.failed(new Exception("error"))
      })

      queue.enqueue(1)
      queue.enqueue(Seq(2, 3, 4))
      queue.enqueue(5)
      val future = queue.enqueue(Seq(6, 7, 8))
      Await.result(future, 1.second)
      count shouldEqual 8
    }*/

    scenario("ignore exceptions in posted jobs") {
      var count = 0
      val queue = new SerialProcessingQueue[Int]({ evs => count += evs.length; Future.successful({}) })

      queue.enqueue(1)
      val f = queue.post(throw new Exception("ex"))
      intercept[Exception] { Await.result(f, 1.second) }
      Await.result(queue.enqueue(2) ,1.second)
      count shouldEqual 2
    }

    scenario("ignore failures in posted jobs") {
      var count = 0
      val queue = new SerialProcessingQueue[Int]({ evs => count += evs.length; Future.successful({}) })

      queue.enqueue(1)
      val f = queue.post(Future.failed(new Exception("ex")))
      intercept[Exception] { Await.result(f, 1.second) }
      Await.result(queue.enqueue(2) ,1.second)
      count shouldEqual 2
    }
  }
}
