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

import scala.collection.{breakOut, mutable}

// Basically a Set[(A, B)] optimized for read performance from both sides.
case class BiRelation[A, B] private (aftersets: Map[A, Set[B]], foresets: Map[B, Set[A]]) extends Iterable[(A, B)] {
  def +(a: A, b: B): BiRelation[A, B] = BiRelation(add(a, b, aftersets), add(b, a, foresets))
  def -(a: A, b: B): BiRelation[A, B] = BiRelation(remove(a, b, aftersets), remove(b, a, foresets))

  def ++(other: TraversableOnce[(A, B)]): BiRelation[A, B] = BiRelation(iterator ++ other)
  def --(other: TraversableOnce[(A, B)]): BiRelation[A, B] = {
    val items = other.toSet
    BiRelation(iterator.filterNot(items))
  }

  def removeLeft(a: A): BiRelation[A, B] = BiRelation(aftersets - a, foresets.keys.foldLeft(foresets)((accu, b) => diff(b, foresets(b) - a, foresets)))
  def removeAllLeft(a: Traversable[A]): BiRelation[A, B] = BiRelation(aftersets -- a, foresets.keys.foldLeft(foresets)((accu, b) => diff(b, foresets(b) -- a, foresets)))
  def removeRight(b: B): BiRelation[A, B] = BiRelation(aftersets.keys.foldLeft(aftersets)((accu, a) => diff(a, aftersets(a) - b, aftersets)), foresets - b)
  def removeAllRight(b: Traversable[B]): BiRelation[A, B] = BiRelation(aftersets.keys.foldLeft(aftersets)((accu, a) => diff(a, aftersets(a) -- b, aftersets)), foresets -- b)

  def addToAfterset(a: A, bs: Set[B]) = BiRelation[A, B](aftersets.updated(a, aftersets.get(a).fold2(bs, _ ++ bs)), bs.foldLeft(foresets)((accu, b) => add(b, a, accu)))
  def removeFromAfterset(a: A, bs: Set[B]) = BiRelation[A, B](aftersets.get(a).fold2(aftersets, minuend => diff(a, minuend -- bs, aftersets)), bs.foldLeft(foresets)((accu, b) => remove(b, a, accu)))
  def addToForeset(b: B, as: Set[A]) = BiRelation[A, B](as.foldLeft(aftersets)((accu, a) => add(a, b, accu)), foresets.updated(b, foresets.get(b).fold2(as, _ ++ as)))
  def removeFromForeset(b: B, as: Set[A]) = BiRelation[A, B](as.foldLeft(aftersets)((accu, a) => remove(a, b, accu)), foresets.get(b).fold2(foresets, minuend => diff(b, minuend -- as, foresets)))

  def afterset(a: A): Set[B] = aftersets.getOrElse(a, Set.empty)
  def foreset(b: B): Set[A] = foresets.getOrElse(b, Set.empty)

  def contains(a: A, b: B): Boolean = aftersets.contains(a) && foresets.contains(b)
  def containsLeft(a: A): Boolean = aftersets.contains(a)
  def containsRight(b: B): Boolean = foresets.contains(b)

  private def add[C, D](c: C, d: D, m: Map[C, Set[D]]): Map[C, Set[D]] = m.get(c).fold2(m.updated(c, Set(d)), ds => m.updated(c, ds + d))
  private def remove[C, D](c: C, d: D, m: Map[C, Set[D]]): Map[C, Set[D]] = m.get(c).fold2(m, ds => diff(c, ds - d, m))
  private def diff[C, D](c: C, diff: Set[D], m: Map[C, Set[D]]): Map[C, Set[D]] = if (diff.isEmpty) m - c else m.updated(c, diff)

  override def iterator: Iterator[(A, B)] = aftersets.flatIterator
  override def isEmpty: Boolean = aftersets.isEmpty
}

object BiRelation {
  private val NIL = new BiRelation[Nothing, Nothing](Map.empty, Map.empty)
  def empty[A, B]: BiRelation[A, B] = NIL.asInstanceOf[BiRelation[A, B]]

  def apply[A, B](relations: TraversableOnce[(A, B)]): BiRelation[A, B] = {
    val l = mutable.Map.empty[A, mutable.Builder[B, Set[B]]]
    val r = mutable.Map.empty[B, mutable.Builder[A, Set[A]]]

    for (elem <- relations) {
      l.getOrElseUpdate(elem._1, Set.newBuilder[B]) += elem._2
      r.getOrElseUpdate(elem._2, Set.newBuilder[A]) += elem._1
    }

    new BiRelation[A, B](l.map { case (k, v) => (k, v.result) }(breakOut), r.map { case (k, v) => (k, v.result) }(breakOut))
  }
}
