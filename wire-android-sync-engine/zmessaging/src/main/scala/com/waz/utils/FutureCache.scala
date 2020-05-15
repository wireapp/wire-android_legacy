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

import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.concurrent.{Promise, Future}

class FutureCache[A] {
  private val ref = new AtomicReference[SoftReference[Future[A]]]()

  @tailrec final def cached(compute: => Future[A]): Future[A] = {
    val soft = ref.get
    val cachedFuture = if (soft != null) soft.get else null
    if (cachedFuture != null) cachedFuture
    else {
      val promise = Promise[A]
      val future = promise.future
      if (ref.compareAndSet(soft, new SoftReference(future))) {
        promise.completeWith(compute)
        future
      } else cached(compute)
    }
  }

  def clear(): Unit = ref.set(null)
}
