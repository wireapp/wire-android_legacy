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

import scala.collection.breakOut
import scala.collection.immutable.HashMap

final case class SeqMap[K, +V](keys: IndexedSeq[K], byKey: Map[K, V]) {
  @inline def values = keys map byKey
  @inline def valuesIterator = keys.iterator map byKey
  @inline def contains(key: K) = byKey contains key
  @inline def apply(key: K): Option[V] = byKey get key
  @inline def at(index: Int): V = byKey(keys(index))
  @inline def get(index: Int): Option[(K, V)] = if (index >= 0 && index < keys.size) Some((keys(index), byKey(keys(index)))) else None
  @inline def size = keys.size
  @inline def filter(p: K => Boolean) = SeqMap(keys filter p, byKey filterKeys p)
  @inline def filterNot(p: K => Boolean) = SeqMap(keys filterNot p, byKey filterKeys (p andThen (! _)))
  @inline def foreach(f: (K, V) => Unit): Unit = keys.foreach(k => f(k, byKey(k)))
}
object SeqMap {
  private val NIL = new SeqMap[Nothing, Nothing](Vector.empty, HashMap.empty)
  def empty[K, V]: SeqMap[K, V] = NIL.asInstanceOf[SeqMap[K, V]]
  def apply[K, V, A](items: Traversable[A])(f: A => K, g: A => V): SeqMap[K, V] = new SeqMap(items.map(f)(breakOut): Vector[K], items.map(a => f(a) -> g(a))(breakOut): HashMap[K, V])
}
