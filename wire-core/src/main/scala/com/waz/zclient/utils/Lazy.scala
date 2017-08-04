/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.utils

import com.waz.utils.returning

/*
  A lazy val with a tearDown method. You can call tearDown without checking if the value is initialized already.
 */
class Lazy[T](init: () => T, _tearDown: (T) => Unit) {
  private var initialized = false

  private lazy val value: T = returning( init() ){ _ => initialized = true }

  def apply(): T = value

  def tearDown(): Unit = if (initialized) _tearDown(value)
}

object Lazy {
  def apply[T](init: => T): Lazy[T] = new Lazy[T](() => init, (_: T) => {})
  def apply[T](init: => T, _tearDown: (T) => Unit): Lazy[T] = new Lazy[T](() => init, _tearDown)
}
