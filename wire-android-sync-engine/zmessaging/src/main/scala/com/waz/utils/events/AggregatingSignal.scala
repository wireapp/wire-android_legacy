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

import java.util.concurrent.atomic.AtomicReference

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.threading.Threading

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AggregatingSignal[A, B](source: EventStream[A], load: => Future[B], f: (B, A) => B, stashing: Boolean = true)
  extends Signal[B] with EventListener[A] with DerivedLogTag {

  private object valueMonitor
  private val loadId = new AtomicReference[AnyRef]
  @volatile private var stash = Vector.empty[A]

  override protected[events] def onEvent(event: A, currentContext: Option[ExecutionContext]): Unit = valueMonitor synchronized {
    if (loadId.get eq null) value.foreach(v => AggregatingSignal.this.set(Some(f(v, event)), currentContext))
    else if (stashing) stash :+= event
  }

  private def startLoading(id: AnyRef) = {
    load.onComplete {
      case Success(s) if loadId.get eq id =>
        valueMonitor.synchronized {
          AggregatingSignal.this.set(Some(stash.foldLeft(s)(f)), Some(context))
          loadId.compareAndSet(id, null)
          stash = Vector.empty
        }
      case Failure(ex) if loadId.get eq id =>
        valueMonitor.synchronized(stash = Vector.empty)
        error(l"load failed", ex)
      case _ =>
        verbose(l"delegate is no longer the current one, discarding loaded value")
    } (context)
  }

  private lazy val context = executionContext.getOrElse(Threading.Background)

  override def onWire(): Unit = {
    stash = Vector.empty
    val id = new AnyRef
    loadId.set(id)
    source.subscribe(this) // important to subscribe before starting to load
    startLoading(id)
  }

  override def onUnwire(): Unit = {
    loadId.set(null)
    source.unsubscribe(this)
  }
}

