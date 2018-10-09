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

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit, TimeoutException}

import com.waz.ZLog
import com.waz.ZLog.LogTag
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture.CancelException
import com.waz.utils.returning

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success, Try}

class CancellableFutureSpec extends AndroidFreeSpec {
//  implicit val tag: LogTag = "CancellableFutureSpec"
//  private implicit lazy val dispatcher = Threading.Background
//
//  feature("Await result") {
//
//    scenario("Await fast task result") {
//      Await.result(CancellableFuture(1), 10.millis) shouldEqual 1
//    }
//
//    scenario("Await longer running task result") {
//      Await.result(CancellableFuture {
//        Thread.sleep(100)
//        2
//      }, 150.millis) shouldEqual 2
//    }
//  }
//
//  scenario("Cancel long running task") {
//    val future = CancellableFuture(Thread.sleep(100))
//    future.cancel()
//
//    intercept[CancelException] {
//      Await.result(future, 10.millis) // cancelled task should return immediately
//    }
//  }
//
//  scenario("Lift and cancel regular Future") {
//    val future = CancellableFuture.lift(Future(Thread.sleep(100)))
//    future.cancel()
//
//    intercept[CancelException] {
//      Await.result(future, 10.millis) // cancellable future reports cancellation immediately, even if original feature is still running
//    }
//  }
//
//  scenario("Propagate cancel via promise") {
//    val p1 = Promise[Unit]
//    val f1 = new CancellableFuture(p1)
//    val p2 = Promise[Unit]
//    val f2 = new CancellableFuture(p2)
//    p1.tryCompleteWith(f2)
//
//    f2.cancel()
//
//    intercept[CancelException](Await.result(f1, 10.millis))
//  }
//
//  feature("Delayed execution") {
//
//    scenario("Delay future should not return before requested time elapses") {
//      intercept[TimeoutException] {
//        Await.result(CancellableFuture.delay(200.millis), 150.millis)
//      }
//    }
//
//    scenario("Wait for delay to complete") {
//      Await.result(CancellableFuture.delay(100.millis), 110.millis) shouldEqual {}
//    }
//
//    scenario("Cancel delay") {
//      val future = CancellableFuture.delay(200.millis)
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 10.millis) // cancelled task should return immediately
//      }
//    }
//
//    scenario("Cancel delayed") {
//      val future = CancellableFuture.delayed(200.millis)(1)
//
//      intercept[CancelException] {
//        future.cancel()
//        Await.result(future, 10.millis) // cancelled task should return immediately
//      }
//    }
//  }
//
//  feature("Map") {
//    scenario("Await for mapped result") {
//      Await.result(CancellableFuture(1) map (_ + 1), 10.millis) shouldEqual 2
//      Await.result(CancellableFuture(1) map (_ + 1) map (_ + 1), 10.millis) shouldEqual 3
//      Await.result(CancellableFuture.delayed(100.millis)(1) map (_ + 1), 120.millis) shouldEqual 2
//    }
//
//    scenario("Cancel mapped future before map is executed") {
//      @volatile var mapped = false
//      val future = CancellableFuture.delay(100.millis).map { _ => mapped = true }
//      future.cancel()
//      Await.ready(future, 10.millis) // should return immediately
//      Thread.sleep(150)
//      mapped shouldEqual false
//    }
//
//    scenario("Cancel mapped future when map function is being executed") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture {
//        1
//      }.map { _ =>
//        mapped = true
//        Thread.sleep(100)
//        completed = true
//        2
//      }
//      Thread.sleep(10)
//      mapped shouldEqual true
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 10.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      }
//      Thread.sleep(100)
//      completed shouldEqual true // map function will complete execution after future was cancelled
//    }
//  }
//
//  feature("FlatMap") {
//
//    scenario("Await for flatMapped result") {
//      Await.result(CancellableFuture(1) flatMap { v => CancellableFuture(v + 1) }, 100.millis) shouldEqual 2
//      Await.result(CancellableFuture(1) flatMap { v => CancellableFuture(v + 1) } flatMap { v => CancellableFuture(v + 1) }, 100.millis) shouldEqual 3
//    }
//
//    scenario("Execute flatMap recursively") {
//
//      def sum(n: Int, acc: Int = 0): CancellableFuture[Int] = CancellableFuture(n) flatMap {
//        case 0 => CancellableFuture.successful(acc)
//        case s => sum(s - 1, acc + s)
//      }
//
//      Await.result(sum(100), 100.millis) shouldEqual 5050
//      Await.result(sum(1000), 1000.millis) shouldEqual 500500
//      Await.ready(sum(100000), 5000.millis) // this will take more time, but should complete pretty fast
//    }
//
//    scenario("Execute recursive flatMap with high memory usage") {
//
//      def run(n: Int): CancellableFuture[Array[Int]] = CancellableFuture(new Array[Int](32 * 1024 * 1024)) flatMap { arr =>
//        if (n == 0) CancellableFuture.successful(arr)
//        else run(n - 1)
//      }
//
//      Await.result(run(100), 20.seconds)
//    }
//
//    scenario("Cancel flatMapped future before flatMap is executed") {
//      @volatile var mapped = false
//      val future = CancellableFuture.delay(100.millis).flatMap { _ =>
//        mapped = true
//        CancellableFuture.successful(1)
//      }
//      future.cancel()
//      Await.ready(future, 10.millis) // should return immediately
//      Thread.sleep(150)
//      mapped shouldEqual false
//    }
//
//    scenario("Cancel flatMapped future when flatMap function is being executed") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture(1) .flatMap { _ =>
//        mapped = true
//        Thread.sleep(100)
//        completed = true
//        CancellableFuture.successful(2)
//      }
//      Thread.sleep(10)
//      mapped shouldEqual true
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 10.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      }
//      Thread.sleep(250)
//      completed shouldEqual true // map function will complete execution after future was cancelled
//    }
//
//    scenario("Cancel while flatMapped future is executing") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture {
//        1
//      }.flatMap { _ =>
//        mapped = true
//        CancellableFuture.delay(100.millis)
//      }.flatMap { _ =>
//        completed = true
//        CancellableFuture.successful(3)
//      }
//      Thread.sleep(10)
//      mapped shouldEqual true
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 10.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      }
//      Thread.sleep(100)
//      completed shouldEqual false // second flatMap should never be executed
//    }
//
//    scenario("Cancel while second flatMapped future is executing") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture(1) .flatMap { _ =>
//        CancellableFuture { 2 }
//      } .flatMap { _ =>
//        mapped = true
//        CancellableFuture.delay(100.millis)
//      }.flatMap { _ =>
//        completed = true
//        CancellableFuture.successful(3)
//      }
//      Thread.sleep(10)
//      mapped shouldEqual true
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 10.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      }
//      Thread.sleep(100)
//      completed shouldEqual false // second flatMap should never be executed
//    }
//
//    scenario("Cancelling base feature should cancel derived ones") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture.delay(100.millis)
//      val future1 = future flatMap { _ =>
//        mapped = true
//        CancellableFuture.delayed(100.millis)(completed = true)
//      }
//      future.cancel()
//      intercept[CancelException] {
//        Await.result(future1, 10.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      }
//      Thread.sleep(110)
//      mapped shouldEqual false
//    }
//
//    scenario("Cancelling base once completed has no effect on derived ones") {
//      @volatile var mapped = false
//      @volatile var completed = false
//      val future = CancellableFuture.delay(10.millis)
//      val future1 = future flatMap { _ =>
//        mapped = true
//        CancellableFuture.delayed(100.millis)(completed = true)
//      }
//      Thread.sleep(20)
//      future.cancel()
//      Await.result(future1, 110.millis) // cancellable future reports cancellation immediately, even if some computation is still running
//      mapped shouldEqual true
//      completed shouldEqual true
//    }
//
//    scenario("Cancel while inner most future is executing") {
//      @volatile var completed = false
//
//      def sum(n: Int, acc: Int = 0): CancellableFuture[Int] = CancellableFuture(n) flatMap {
//        case 0 => CancellableFuture.delayed(2.seconds){ completed = true; acc }
//        case s => sum(s - 1, acc + s)
//      }
//
//      val future = sum(1000)
//      Thread.sleep(100)
//      future.cancel()
//      intercept[CancelException] {
//        Await.result(future, 10.millis)
//      }
//
//      val future1 = sum(100000)
//      Thread.sleep(1000)
//      future1.cancel()
//      intercept[CancelException] {
//        Await.result(future1, 10.millis)
//      }
//
//      Thread.sleep(2000)
//      completed shouldEqual false // both futures were cancelled, delayed task should never be executed
//    }
//  }
//
//  feature("Delay") {
//    scenario("Execute multiple delays at once") {
//      Await.result(CancellableFuture.sequence[Boolean](Seq.fill(100) {
//        val start = System.currentTimeMillis()
//        val delay = Random.nextInt(500).millis
//        CancellableFuture.delay(delay) map { _ =>
//          val actualDelay = System.currentTimeMillis() - start
//          actualDelay >= delay.toMillis && actualDelay < delay.toMillis + 100
//        }
//      }), 1.second) shouldEqual Seq.fill(100)(true)
//    }
//  }
//
//  feature("Timeout") {
//
//    scenario("Run long delay with small timeout") {
//      @volatile var result: Try[Unit] = Success(())
//      val latch = new CountDownLatch(1)
//      CancellableFuture.delay(1.second).withTimeout(100.millis).onComplete { res =>
//        result = res
//        latch.countDown()
//      }
//
//      latch.await(150, TimeUnit.MILLISECONDS)
//      result should beMatching({ case Failure(_: TimeoutException) => true })
//    }
//
//    scenario("Execute multiple delays with timeout") {
//      Await.result(CancellableFuture.sequence[Boolean](Seq.fill(100) {
//        CancellableFuture.delay(1.second).withTimeout(Random.nextInt(500).millis).map(_ => false).recover { case _: TimeoutException => true }
//      }), 600.millis) shouldEqual Seq.fill(100)(true)
//    }
//  }
//
//  feature("Recover") {
//    scenario("recoverWith from error") {
//      val f = dispatcher(throw new RuntimeException("error")) .recoverWith {
//        case e: RuntimeException => CancellableFuture.delayed(100.millis)(1)
//      }
//      Await.result(f, 1.second) shouldEqual 1
//    }
//
//    scenario("recover from source cancel") {
//      val f = CancellableFuture.delayed(1.second)(1)
//      val f1 = f.recover {
//        case _: CancelException => 2
//      }
//
//      f.cancel()
//      Await.result(f1, 100.millis) shouldEqual 2
//    }
//
//    scenario("cancel future with recover") {
//      val f = CancellableFuture.delayed(1.second)(1).recover {
//        case _: CancelException => 2
//      }
//
//      f.cancel()
//      intercept[CancelException] {
//        Await.result(f, 100.millis)
//      }
//    }
//
//    scenario("recover from timeout") {
//      val f = CancellableFuture.delayed(1.second)(1).withTimeout(50.millis).recover {
//        case _: TimeoutException => 2
//      }
//
//      Await.result(f, 200.millis) shouldEqual 2
//    }
//
//    scenario("recover from exception") {
//      val f = CancellableFuture.delayed(10.millis)(throw new RuntimeException).recover {
//        case _: RuntimeException => 1
//      }
//
//      Await.result(f, 200.millis) shouldEqual 1
//    }
//
//    scenario("recover with exception") {
//      val f = CancellableFuture.delayed(1.second)(throw new RuntimeException).recover {
//        case e: CancelException => throw e
//        case _: RuntimeException => 1
//      }
//
//      f.cancel()("cancelled")
//
//      intercept[CancelException] {
//        Await.result(f, 200.millis)
//      }
//    }
//  }
//
//  feature("Cancelling") {
//
//    scenario("Cancel already completed future") {
//      val f = CancellableFuture.delayed(10.millis)(1)
//      Await.result(f, 100.millis) shouldEqual 1
//      f.cancel()
//      Await.result(f, 100.millis) shouldEqual 1
//    }
//
//    scenario("Cancel delayed task") {
//      @volatile var done = false
//      val future = CancellableFuture.delayed(500.millis)(done = true)
//      Thread.sleep(100)
//      future.cancel()
//      intercept[CancelException] {
//        Await.result(future, 1.second)
//      }
//      done shouldEqual false
//    }
//
//    scenario("Cancel recursive mapped task") {
//      var count = 0
//
//      def doSomething(): CancellableFuture[Unit] = {
//        count += 1
//        CancellableFuture.delay(100.millis).flatMap(_ => doSomething())
//      }
//
//      val future = doSomething()
//      Thread.sleep(100)
//      future.cancel()
//      Thread.sleep(1.second.toMillis)
//      count should be < 5
//      intercept[CancelException] {
//        Await.result(future, 2.seconds)
//      }
//    }
//
//    scenario("Cancel mapped task on limited dispatch queue") {
//      @volatile var done = false
//      val queue = new LimitedDispatchQueue(4)
//      val future = CancellableFuture.delayed(500.millis)(done = true)(queue)
//      Thread.sleep(100)
//      future.cancel()
//
//      intercept[CancelException] {
//        Await.result(future, 1.second)
//      }
//      done shouldEqual false
//    }
//
//    scenario("Cancel with common ancestor") {
//
//      val common = CancellableFuture.delayed(500.millis)(1)
//      val f1 = common.map(_ + 1)
//      val f2 = common.map(_ + 2)
//
//      f1.cancel()
//      intercept[CancelException](Await.result(f1, 1.second))
//      // this shows very dangerous side effect of cancel operation
//      // due to this, we shouldn't use CancellableFutures by default
//      intercept[CancelException](Await.result(f2, 1.second))
//    }
//
//    scenario("Prevent common ancestor cancelling") {
//
//      val common = CancellableFuture.delayed(500.millis)(1).future
//      val f1 = CancellableFuture.lift(common).map(_ + 1)
//      val f2 = CancellableFuture.lift(common).map(_ + 2)
//
//      f1.cancel()
//      intercept[CancelException](Await.result(f1, 1.second))
//      f2 should eventually(be(3))
//    }
//  }
//
//  /**
//    * I'll leave this here for future reference:
//    *
//    * Note that reportFailure is only called when an execption is thrown inside Future.onComplete. After delving into the
//    * implementation of Future, one can find that map, flatmap, recover, filter etc (all methods that return Future[T]) all
//    * try/catch any possible exceptions and encode the failure in the return type.
//    *
//    * Only exceptions in foreach/onSuccess/onFailure or in a raw onComplete callback will trigger the method
//    *
//    * To test, try moving the exception to different future callbacks
//    */
//  scenario("Test threads") {
//
//    val dispatcher = new ExecutionContext {
//      val executor = Executors.newFixedThreadPool(1)
//      override def execute(runnable: Runnable): Unit = {
//        ZLog.verbose(s"Running: $runnable")
//        executor.execute(runnable)
//      }
//
//      override def reportFailure(cause: Throwable): Unit = {
//        ZLog.error("Test failed!", cause)
//      }
//    }
//
//    def f = returning(Future {
//      ZLog.verbose("What...")("TEST")
//      throw new Exception("failed the test")
//      ZLog.verbose("What...2")("TEST")
//    } (dispatcher)
//    )(_.failed.foreach(throw _)(dispatcher))
//
//    val f2 = f.map(_ => println("won't print - will however prevent app crashing..."))(dispatcher)
//
////    result(f2)
//
////    Thread.sleep(3000)
//
//  }
}
