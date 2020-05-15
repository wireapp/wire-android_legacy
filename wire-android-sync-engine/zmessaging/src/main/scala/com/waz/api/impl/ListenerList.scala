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
package com.waz.api.impl

import java.lang.ref.WeakReference

class ListenerList[A <: AnyRef] {
  private var listeners = Vector.empty[WeakReference[A]]

  def add(listener: A): Unit = listeners = nonEmptyListeners :+ new WeakReference(listener)

  def remove(listener: A): Unit = listeners = nonEmptyListeners.filter(_.get ne listener)

  def notify(f: A => Unit): Unit = {
    listeners = nonEmptyListeners
    listeners.foreach(_.get match {
      case null =>
      case l    => f(l)
    })
  }

  def size = listeners.size

  private def nonEmptyListeners = if (listeners.exists(_.get eq null)) listeners.filter(_.get ne null) else listeners
}
