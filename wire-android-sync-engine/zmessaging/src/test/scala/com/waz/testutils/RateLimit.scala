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
package com.waz.testutils

import java.util.concurrent.atomic.AtomicReference

import com.waz.threading.CancellableFuture._

import scala.concurrent.duration._
import scala.concurrent.duration.Duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, FiniteDuration}

case class RateLimit(interval: FiniteDuration) {
  val lastAccess = new AtomicReference(Deadline.now - 1.nanos)
  def apply[A](f: => Future[A])(implicit context: ExecutionContext): Future[A] = {
    val recent = lastAccess.get
    val nominalDelay = recent - Deadline.now
    val effectiveDelay = if (nominalDelay < Zero) Zero else nominalDelay
    if (lastAccess.compareAndSet(recent, Deadline.now + effectiveDelay + interval)) delay(effectiveDelay).future flatMap (_ => f)
    else apply(f)
  }
}
