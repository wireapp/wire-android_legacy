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

import java.util.concurrent.CountDownLatch

import scala.concurrent.duration.FiniteDuration

class ReusableCountDownLatch {
  private object monitor
  @volatile private var latch = new CountDownLatch(0)

  def await(): Unit = latch.await()
  def await(d: FiniteDuration) = latch.await(d.length, d.unit)
  def ofSize(size: Int)(f: CountDownLatch => Unit): Unit = monitor.synchronized {
    latch = new CountDownLatch(size)
    try f(latch) finally {
      while (latch.getCount > 0) latch.countDown()
      latch = new CountDownLatch(0)
    }
  }
}
