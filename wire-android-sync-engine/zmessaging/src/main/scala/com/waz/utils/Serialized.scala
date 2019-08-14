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

import com.waz.threading.{CancellableFuture, SerialDispatchQueue}

import scala.collection.mutable
import scala.concurrent.Future

object Serialized {
  private implicit val dispatcher = new SerialDispatchQueue(name = "Serializing")
  
  private val locks = new mutable.HashMap[Any, Future[_]]
  
  def apply[A](key: Any*)(body: => CancellableFuture[A]): CancellableFuture[A] = dispatcher {
    val future = locks.get(key).fold(body) { lock =>
      CancellableFuture.lift(lock.recover { case _ => }) flatMap(_ => body)
    }
    val lock = future.future
    locks += (key -> lock)
    future.onComplete {
      case _ => if (locks.get(key).contains(lock)) locks -= key
    }
    future
  }.flatten

  def future[A](key: Any*)(body: => Future[A]): Future[A] = Future {
    val future = locks.get(key).fold(body) { lock =>
      lock.recover { case _ => } flatMap(_ => body)
    }
    locks += (key -> future)
    future.onComplete {
      case _ => if (locks.get(key).contains(future)) locks -= key
    }
    future
  }.flatten
}
