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
package com.waz.utils.events

import android.app.{Activity, Fragment, Service}
import android.view.View

trait EventContext {
  private object lock

  private[this] var started = false
  private[this] var destroyed = false
  private[this] var observers = Set.empty[Subscription]

  protected implicit def eventContext: EventContext = this

  override protected def finalize(): Unit = {
    lock.synchronized { if (! destroyed) onContextDestroy() }
    super.finalize()
  }

  def onContextStart(): Unit = {
    lock.synchronized {
      if (! started) {
        started = true
        observers foreach (_.subscribe()) // XXX during this, subscribe may call Observable#onWire with in turn may call register which will change observers
      }
    }
  }

  def onContextStop(): Unit = {
    lock.synchronized {
      if (started) {
        started = false
        observers foreach (_.unsubscribe())
      }
    }
  }

  def onContextDestroy(): Unit = {
    lock.synchronized {
      destroyed = true
      val observersToDestroy = observers
      observers = Set.empty
      observersToDestroy foreach (_.destroy())
    }
  }

  def register(observer: Subscription): Unit = {
    lock.synchronized {
      assert(!destroyed, "context already destroyed")

      if (! observers.contains(observer)) {
        observers += observer
        if (started) observer.subscribe()
      }
    }
  }

  def unregister(observer: Subscription): Unit =
    lock.synchronized(observers -= observer)

  def isContextStarted: Boolean = lock.synchronized(started && ! destroyed)
}

object EventContext {

  object Implicits {
    implicit val global: EventContext = EventContext.Global
  }

  object Global extends EventContext {
    override def register(observer: Subscription): Unit = () // do nothing, global context will never need the observers (can not be stopped)
    override def unregister(observer: Subscription): Unit = ()
    override def onContextStart(): Unit = ()
    override def onContextStop(): Unit = ()
    override def onContextDestroy(): Unit = ()
    override def isContextStarted: Boolean = true
  }
}

trait ActivityEventContext extends Activity with EventContext {

  override def onResume(): Unit = {
    onContextStart()
    super.onResume()
  }

  override def onPause(): Unit = {
    super.onPause()
    onContextStop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    onContextDestroy()
  }
}

trait FragmentEventContext extends Fragment with EventContext {

  override def onResume(): Unit = {
    onContextStart()
    super.onResume()
  }

  override def onPause(): Unit = {
    super.onPause()
    onContextStop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    onContextDestroy()
  }
}

trait ViewEventContext extends View with EventContext {

  private var attached = false

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()

    attached = true
    if (getVisibility != View.GONE) onContextStart()
  }

  override def setVisibility(visibility: Int): Unit = {
    super.setVisibility(visibility)

    if (visibility != View.GONE && attached) onContextStart()
    else onContextStop()
  }

  override def onDetachedFromWindow(): Unit = {
    super.onDetachedFromWindow()

    attached = false
    onContextStop()
  }
}

trait ServiceEventContext extends Service with EventContext {

  override def onCreate(): Unit = {
    super.onCreate()
    onContextStart()
  }

  override def onDestroy(): Unit = {
    onContextStop()
    onContextDestroy()
    super.onDestroy()
  }
}
