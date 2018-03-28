/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}

import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.WireContext
import org.robolectric.Robolectric
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TestUtils {

  implicit val eventContext = EventContext.Implicits.global
  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val logTag = com.waz.ZLog.ImplicitTag.implicitLogTag

  val timeout = 1000

  def signalTest[A](signal: Signal[A])(test: A => Boolean)(trigger: => Unit)(implicit printVals: PrintValues, timeoutMillis: Int = timeout): Unit = {
    signal.disableAutowiring()
    trigger
    if (printVals) println("****")
    awaitUi(signal.filter { value => if (printVals) println(value); test (value) }.head)(timeoutMillis.millis)
    if (printVals) println("****")
  }

  // active wait to make sure UI thread is not blocked (Robolectric blocks it)
  def awaitUi[A](f: Future[A])(implicit timeout: FiniteDuration = 5.seconds): A = {
    val endTime = Instant.now + timeout
    while (!f.isCompleted && Instant.now.isBefore(endTime)) {
      Robolectric.runBackgroundTasks()
      Robolectric.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(50) // sleep a bit
    }
    f.value match {
      case None => throw new TimeoutException("Future did not complete within supplied timeout")
      case Some(Failure(ex)) => throw ex
      case Some(Success(res)) => res
    }
  }

  type PrintValues = Boolean

  implicit class RichLatch(latch: CountDownLatch) {
    def waitDuration(implicit duration: Duration): Unit = latch.await(duration.toMillis, TimeUnit.MILLISECONDS)
  }
}


abstract class TestWireContext extends WireContext {
  override def eventContext = EventContext.Implicits.global
}
/*
class ViewTestActivity extends FragmentActivity with ActivityHelper {

  var inj: Injector = _

  override lazy val injector = inj
}
*/
