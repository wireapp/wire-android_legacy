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

import scala.collection.mutable
import scala.ref.{ReferenceQueue, WeakReference}

class WeakMemCache[Key, V <: AnyRef] {
  private object lock

  private val queue = new ReferenceQueue[V]
  private val cache = new mutable.HashMap[Key, WeakReferenceWithKey]()

  def apply(k: Key, factory: => V): V = lock.synchronized {
    def drop(): Unit = queue.poll match {
      case Some(ref: WeakReferenceWithKey) =>
        cache.remove(ref.key)
        drop()
      case _ => // done
    }

    drop()

    cache.get(k).flatMap(_.get).getOrElse {
      returning(factory) { v => cache.put(k, new WeakReferenceWithKey(k, v, queue)) }
    }
  }

  def clear() = lock.synchronized {
    cache.clear()
  }

  class WeakReferenceWithKey(val key: Key, item: V, queue: ReferenceQueue[V]) extends WeakReference[V](item, queue)
}
