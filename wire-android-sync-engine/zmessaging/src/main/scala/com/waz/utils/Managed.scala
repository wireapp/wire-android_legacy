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

import java.io.Closeable

sealed trait Managed[+A] {
  def acquire[B](f: A => B): B
  def map[B](f: A => B): Managed[B] = new MapManaged(this, f)
  def flatMap[B](f: A => Managed[B]): Managed[B] = new FlatMapManaged[A, B](this, f)
  def foreach(f: A => Unit): Unit = acquire(f)
}

object Managed {
  def apply[A: Cleanup](create: => A): Managed[A] = new ManagedCleanup[A](create)
  def create[A](create: => A)(clean: A => Unit): Managed[A] = new ManagedCleanup[A](create)(Cleanup.create(clean))
}

class ManagedCleanup[A](create: => A)(implicit cleanup: Cleanup[A]) extends Managed[A] {
  def acquire[B](f: A => B): B = {
    val resource = create
    try f(resource) finally cleanup(resource)
  }
}

class MapManaged[A, B](ma: Managed[A], f: A => B) extends Managed[B] {
  def acquire[C](g: B => C): C = ma.acquire(f andThen g)
}

class FlatMapManaged[A, B](ma: Managed[A], f: A => Managed[B]) extends Managed[B] {
  def acquire[C](g: B => C): C = ma.acquire(a => f(a).acquire(g))
}

trait Cleanup[-A] {
  def apply(a: A): Unit
}
object Cleanup {
  def create[A](clean: A => Unit): Cleanup[A] = new Cleanup[A] {
    override def apply(a: A): Unit = clean(a)
  }

  val empty: Cleanup[Any] = create(_ => ())

  implicit lazy val CloseableCleanup: Cleanup[Closeable] = create(_.close())
}
