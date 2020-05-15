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

import org.robolectric.Robolectric
import org.scalactic.source
import org.scalatest.time.Span
import org.scalatest.{Informer, Informing}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

trait RobolectricUtils { self: Informing =>

  type Timeout = FiniteDuration

  implicit def spanCanBeUsedAsTimeout: Span => Timeout = _.totalNanos.nanos

  def context = Robolectric.application
  implicit def testContext = Robolectric.application

  @tailrec
  final def withDelay[T](body: => T, startAt: Long = System.currentTimeMillis(), delay: FiniteDuration = 100.millis)(implicit timeout: Timeout = 5.seconds): T = {
    Try(body) match {
      case Success(value) => value
      case failure =>
        if (System.currentTimeMillis() - startAt > timeout.toMillis) failure.get else {
          Thread.sleep(delay.toMillis)
          Robolectric.runBackgroundTasks()
          Robolectric.runUiThreadTasksIncludingDelayedTasks()
          withDelay(body, startAt, delay)(timeout)
        }
    }
  }

  def awaitUi(cond: => Boolean, msg: => String = "", interval: FiniteDuration = 100.millis)(implicit timeout: Timeout = 5.seconds) = {
    val end = System.currentTimeMillis() + timeout.toMillis
    while (!cond && end > System.currentTimeMillis()) {
      Thread.sleep(interval.toMillis, (interval.toNanos % 1000000).toInt)
      Robolectric.runBackgroundTasks()
      Robolectric.runUiThreadTasksIncludingDelayedTasks()
    }
    if (!cond) {
      val m = msg
      if (m.isEmpty) throw new TimeoutException(s"awaitUi timed out after: $timeout")
      else throw new TimeoutException(m + s" [timed out after: $timeout]")
    }
  }

  def awaitUi(duration: FiniteDuration): Unit = {
    val end = System.currentTimeMillis() + duration.toMillis
    while (end > System.currentTimeMillis()) {
      Thread.sleep(100L)
      Robolectric.runBackgroundTasks()
      Robolectric.runUiThreadTasksIncludingDelayedTasks()
    }
  }

  def awaitUiFuture[A](future: Future[A])(implicit timeout: Timeout = 5.seconds): Try[A] = {
    awaitUi(future.isCompleted)
    future.value.get
  }
}

object RobolectricUtils extends RobolectricUtils with Informing {
  override protected def info: Informer = new Informer {
    override def apply(message: String, payload: Option[Any])(implicit pos: source.Position): Unit = println(message)
  }
}
