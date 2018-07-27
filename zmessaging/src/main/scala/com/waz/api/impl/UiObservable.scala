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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.{Subscriber, Subscription, UpdateListener}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.ui.{SignalLoading, UiModule}
import com.waz.utils.events.Signal

trait UiObservable extends com.waz.api.UiObservable {
  private val updateListeners = new ListenerList[UpdateListener]

  override def addUpdateListener(listener: UpdateListener): Unit = updateListeners.add(listener)

  override def removeUpdateListener(listener: UpdateListener): Unit = updateListeners.remove(listener)

  def getListenersCount = updateListeners.size

  protected def notifyChanged(): Unit = {
    Threading.assertUiThread()
    debug(s"notifyChanged, $this")
    updateListeners.notify(_.updated())
  }
}

abstract class UiSignal[A]()(implicit ui: UiModule) extends com.waz.api.UiSignal[A] with UiObservable with SignalLoading {

  private var value = Option.empty[A]

  private var subscribers = Vector.empty[Subscriber[A]]

  protected[api] def set(v: A) = {
    if (!value.contains(v)) {
      value = Some(v)
      subscribers foreach (_.next(v))
      notifyChanged()
    }
  }

  override def isEmpty: Boolean = value.isEmpty

  override def get: A = value.getOrElse(throw new IllegalStateException("Called `UiSignal.get` on signal which was not loaded yet. Use `UiSignal.subscribe` instead. Or, if you must, check `isEmpty` before calling `get`."))

  override def subscribe(sub: Subscriber[A]): Subscription = {
    Threading.assertUiThread()

    subscribers = subscribers :+ sub
    value foreach sub.next
    new Subscription {
      override def cancel(): Unit = subscribers = subscribers.filter(_ ne sub)
    }
  }

  override def toString: String = value.toString
}

object UiSignal {
  def apply[A](s: ZMessaging => Signal[A])(implicit ui: UiModule): UiSignal[A] = new UiSignal[A]() {
    addLoader(s) { set }
  }
}
