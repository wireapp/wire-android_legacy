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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, CyclicBarrier, TimeUnit}

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.specs.AndroidFreeSpec
import com.waz.specs.AndroidFreeSpec.DefaultTimeout
import com.waz.testutils.Implicits._
import com.waz.threading.{SerialDispatchQueue, Threading}
import org.scalatest.concurrent.Eventually

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._

/**
  * This test class uses the eventually construct because many of these operations,
  * such as `hasSubscribers()` calls, are asynchronous, and thus you get sporadic
  * test failures otherwise.
  */
class SignalSpec extends AndroidFreeSpec with DerivedLogTag with Eventually {
  import scala.concurrent.ExecutionContext.Implicits.global

  var received = Seq[Int]()
  val capture = (value: Int) => received = received :+ value

  val eventContext = new EventContext() {}

  override protected def beforeEach() = {
    super.beforeEach()
    received = Seq[Int]()
    eventContext.onContextStart()
  }

  override protected def afterEach() = {
    super.afterEach()
    eventContext.onContextStop()
  }

  feature("Basic Signals") {
    scenario("Receive initial value") {

      // given
      val s = Signal(1)

      // when
      s(capture)

      // then
      eventually { received shouldEqual Seq(1) }
    }

    scenario("Basic subscriber lifecycle") {

      // given
      val s = Signal(1)
      eventually { s.hasSubscribers shouldEqual false }

      // when
      val sub = s { _ => () }

      // then
      eventually { s.hasSubscribers shouldEqual true }

      // and when
      sub.destroy()

      // then
      eventually { s.hasSubscribers shouldEqual false }
    }

    scenario("Don't receive events after unregistering a single observer")  {

      // given
      val s = Signal(1)
      val sub = s(capture)
      eventually { s.hasSubscribers shouldEqual true }

      // when
      s ! 2

      // then
      eventually { received shouldEqual Seq(1, 2) }

      // and given
      sub.destroy()
      eventually { s.hasSubscribers shouldEqual false }

      // when
      s ! 3

      // then
      Thread.sleep(200)
      received shouldEqual Seq(1, 2)
    }

    scenario("Don't receive events after unregistering all observers")  {

      // given
      val s = Signal(1)
      s(capture)
      eventually { s.hasSubscribers shouldEqual true }

      // when
      s ! 2

      // then
      eventually { received shouldEqual Seq(1, 2) }

      // and given
      s.unsubscribeAll()
      eventually { s.hasSubscribers shouldEqual false }

      // when
      s ! 3

      // then
      Thread.sleep(200)
      received shouldEqual Seq(1, 2)
    }

    scenario("Signal mutation") {

      // given
      val s = Signal(42)
      s(capture)
      eventually { received shouldEqual Seq(42) }

      // when
      s.mutate(_ + 1)

      // then
      eventually { received shouldEqual Seq(42, 43) }

      // and when
      s.mutate(_ - 1)

      // then
      eventually { received shouldEqual Seq(42, 43, 42) }
    }
  }

  feature("Caching") {
    scenario("Don't send the same value twice") {
      // given
      val s = Signal(1)
      s(capture)
      eventually { s.hasSubscribers shouldEqual true }

      // when
      Seq(1, 2, 2, 1) foreach (s ! _)

      // then
      eventually { received shouldEqual Seq(1, 2, 1) }
    }

    scenario("Idempotent signal mutation") {

      // given
      val s = Signal(42)
      s(capture)
      eventually { received shouldEqual Seq(42) }

      // when
      s.mutate(_ + 1 - 1)

      // then
      Thread.sleep(200)
      received shouldEqual Seq(42)
    }
  }

  feature("For comprehensions") {
    scenario("Simple for comprehension") {

      // given
      val s = Signal(0)
      val s1 = Signal(1)
      val s2 = Signal(2)
      val r = for {
        x <- s
        y <- Seq(s1, s2)(x)
      } yield y * 2
      r(capture)
      eventually { r.currentValue.get shouldEqual 2 }
      eventually { received shouldEqual Seq(2) }

      // when
      s ! 1

      // then
      eventually { r.currentValue.get shouldEqual 4 }
      eventually { received shouldEqual Seq(2, 4) }
    }
  }

  feature("Concurrency") {
    scenario("Many concurrent observer changes") {
      val barrier = new CyclicBarrier(50)
      val num = new AtomicInteger(0)
      val s = Signal(0)

      def add(barrier: CyclicBarrier): Future[Subscription] = Future(blocking{
        barrier.await()
        s { _ => num.incrementAndGet() }
      })

      val subs = Await.result(Future.sequence(Seq.fill(50)(add(barrier))), 10.seconds)
      eventually { s.hasSubscribers shouldEqual true }
      eventually { num.getAndSet(0) shouldEqual 50 }

      s ! 42
      eventually { num.getAndSet(0) shouldEqual 50 }

      val chaosBarrier = new CyclicBarrier(75)
      val removals = Future.traverse(subs.take(25)) (sub => Future(blocking {
        chaosBarrier.await()
        sub.destroy()
      }))
      val adding = Future.sequence(Seq.fill(25)(add(chaosBarrier)))
      val sending = Future.traverse(1 to 25) (n => Future(blocking {
        chaosBarrier.await()
        s ! n
      }))

      val moreSubs = Await.result(adding, 10.seconds)
      Await.result(removals, 10.seconds)
      Await.result(sending, 10.seconds)

      eventually { num.get should be <= 75*25 }
      eventually { num.get should be >= 25*25 }
      eventually { s.hasSubscribers shouldEqual true }

      barrier.reset()
      Await.result(Future.traverse(moreSubs ++ subs.drop(25)) (sub => Future(blocking {
        barrier.await()
        sub.destroy()
      })), 10.seconds)
      eventually { s.hasSubscribers shouldEqual false }
    }

    scenario("Concurrent updates with incremental values") {
      incrementalUpdates((s, r) => s { r.add })
    }

    scenario("Concurrent updates with incremental values with serial dispatch queue") {
      val dispatcher = new SerialDispatchQueue()
      incrementalUpdates((s, r) => s.on(dispatcher) { r.add })
    }

    scenario("Concurrent updates with incremental values and onChanged listener") {
      incrementalUpdates((s, r) => s.onChanged { r.add })
    }

    scenario("Concurrent updates with incremental values and onChanged listener with serial dispatch queue") {
      val dispatcher = new SerialDispatchQueue()
      incrementalUpdates((s, r) => s.onChanged.on(dispatcher) { r.add })
    }

    def incrementalUpdates(listen: (Signal[Int], ConcurrentLinkedQueue[Int]) => Unit) = {
      100 times {
        val signal = Signal(0)
        val received = new ConcurrentLinkedQueue[Int]()

        listen(signal, received)

        val send = new AtomicInteger(0)
        val done = new CountDownLatch(10)
        (1 to 10).foreach(n => Future {
          for (_ <- 1 to 100) {
            val v = send.incrementAndGet()
            signal.mutate(_ max v)
          }
          done.countDown()
        })

        done.await(DefaultTimeout.toMillis, TimeUnit.MILLISECONDS)

        eventually { signal.currentValue.get shouldEqual send.get() }

        val arr = received.asScala.toVector
        eventually { arr shouldEqual arr.sorted }
      }
    }

    scenario("Two concurrent dispatches (global event and background execution contexts)") {
      concurrentDispatches(2, 200, EventContext.Global, Some(Threading.Background), Threading.Background)()
    }

    scenario("Several concurrent dispatches (global event and background execution contexts)") {
      concurrentDispatches(10, 200, EventContext.Global, Some(Threading.Background), Threading.Background)()
    }

    scenario("Many concurrent dispatches (global event and background execution contexts)") {
      concurrentDispatches(100, 200, EventContext.Global, Some(Threading.Background), Threading.Background)()
    }



    scenario("Two concurrent dispatches (subscriber on UI eventcontext)") {
      concurrentDispatches(2, 200, eventContext, Some(Threading.Background), Threading.Background)()
    }

    scenario("Several concurrent dispatches (subscriber on UI event context)") {
      concurrentDispatches(10, 200, eventContext, Some(Threading.Background), Threading.Background)()
    }

    scenario("Many concurrent dispatches (subscriber on UI event context)") {
      concurrentDispatches(100, 100, eventContext, Some(Threading.Background), Threading.Background)()
    }



    scenario("Several asynchronous but serial UI dispatches (subscriber and source on UI event context)") {
      concurrentDispatches(10, 200, eventContext, Some(Threading.Ui), Threading.Ui)()
    }



    scenario("Several concurrent dispatches (global event context, no source context)") {
      concurrentDispatches(10, 200, EventContext.Global, None, Threading.Background)()
    }

    scenario("Several concurrent dispatches (subscriber on UI context, no source context)") {
      concurrentDispatches(10, 200, eventContext, None, Threading.Background)()
    }

    scenario("Several asynchronous but serial UI dispatches (subscriber on UI event context, no source context)") {
      concurrentDispatches(10, 200, eventContext, None, Threading.Ui)()
    }



    scenario("Several concurrent mutations (subscriber on global event context)") {
      concurrentMutations(10, 200, EventContext.Global, Threading.Background)()
    }

    scenario("Several concurrent mutations (subscriber on UI event context)") {
      concurrentMutations(10, 200, eventContext, Threading.Background)()
    }

    scenario("Several asynchronous but serial mutations (subscriber on UI event context)") {
      concurrentMutations(10, 200, eventContext, Threading.Ui)()
    }



    scenario("Several concurrent mutations (subscriber on UI event and execution context)") {
      concurrentMutations(10, 200, eventContext, Threading.Background)(s => g => s.onUi(g)(eventContext))
    }

    scenario("Several concurrent dispatches (subscriber on UI event and execution context)") {
      concurrentDispatches(10, 200, eventContext, Some(Threading.Background), Threading.Background)(s => g => s.onUi(g)(eventContext))
    }
  }

  def concurrentDispatches(dispatches: Int, several: Int, eventContext: EventContext, dispatchExecutionContext: Option[ExecutionContext], actualExecutionContext: ExecutionContext)(subscribe: Signal[Int] => (Int => Unit) => Subscription = s => g => s(g)(eventContext)): Unit =
    concurrentUpdates(dispatches, several, (s, n) => s.set(Some(n), dispatchExecutionContext), actualExecutionContext, subscribe)

  def concurrentMutations(dispatches: Int, several: Int, eventContext: EventContext, actualExecutionContext: ExecutionContext)(subscribe: Signal[Int] => (Int => Unit) => Subscription = s => g => s(g)(eventContext)): Unit =
    concurrentUpdates(dispatches, several, (s, n) => s.mutate(_ + n), actualExecutionContext, subscribe, eventually { _.currentValue.get shouldEqual 55 })

  def concurrentUpdates(dispatches: Int, several: Int, f: (SourceSignal[Int], Int) => Unit, actualExecutionContext: ExecutionContext, subscribe: Signal[Int] => (Int => Unit) => Subscription, additionalAssert: Signal[Int] => Unit = _ => ()): Unit =
    several times {
      val signal = Signal(0)

      @volatile var lastSent = 0
      val received = new AtomicInteger(0)
      val p = Promise[Unit]()

      val _ = subscribe(signal) { i =>
        lastSent = i
        if (received.incrementAndGet() == dispatches + 1) p.trySuccess({})
      }

      (1 to dispatches).foreach(n => Future(f(signal, n))(actualExecutionContext))

      Await.result(p.future, 30.seconds)

      additionalAssert(signal)
      eventually { signal.currentValue.get shouldEqual lastSent }
    }
}
