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

import java.util.concurrent.atomic.AtomicInteger

import com.waz.api.{UiObservable, UpdateListener}
import com.waz.utils.returning

case class UpdateSpy() extends UpdateListener with SpyBase {
  def updated(): Unit = increment()
}
object UpdateSpy {
  def apply(observable: UiObservable): UpdateSpy = returning(UpdateSpy())(observable.addUpdateListener)
}

trait SpyBase {
  private val count = new AtomicInteger(0)
  def numberOfTimesCalled: Int = count.get
  def increment(): Unit = count.incrementAndGet
  def reset(): Unit = count set 0
}
