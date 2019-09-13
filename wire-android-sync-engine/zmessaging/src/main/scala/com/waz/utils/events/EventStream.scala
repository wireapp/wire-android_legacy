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

import java.util.UUID.randomUUID

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Events.Subscriber
import com.waz.utils.{Serialized, returning}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.ref.WeakReference
import scala.util.{Failure, Success}

private[events] trait EventListener[E] {
  // 'currentContext' is the context this method IS run in, NOT the context any subsequent methods SHOULD run in
  protected[events] def onEvent(event: E, currentContext: Option[ExecutionContext]): Unit
}

object EventStream {
  def apply[A]() = new SourceStream[A]
  def union[A](streams: EventStream[A]*): EventStream[A] = new UnionEventStream(streams: _*)

  def wrap[A](source: Signal[A]) = new EventStream[A] with SignalListener { stream =>
    override def changed(ec: Option[ExecutionContext]): Unit = stream.synchronized { source.value foreach (dispatch(_, ec)) }

    override protected def onWire(): Unit = {
      source.subscribe(this)
      source.value foreach (dispatch(_, None))
    }
    override protected def onUnwire(): Unit = source.unsubscribe(this)
  }
}

class SourceStream[E] extends EventStream[E] {
  def !(event: E): Unit = publish(event)
  override def publish(event: E): Unit = dispatch(event, None)
  def publish(event: E, ec: ExecutionContext): Unit = dispatch(event, Some(ec))
}

class EventStream[E] extends EventSource[E] with Observable[EventListener[E]] {

  private object dispatchMonitor

  private def dispatchEvent(event: E, currentExecutionContext: Option[ExecutionContext]): Unit = dispatchMonitor.synchronized {
    notifyListeners(_.onEvent(event, currentExecutionContext))
  }

  protected[events] def dispatch(event: E, sourceContext: Option[ExecutionContext]): Unit = executionContext match {
    case None | `sourceContext` => dispatchEvent(event, sourceContext)
    case Some(ctx) => Future(dispatchEvent(event, executionContext))(ctx)
  }

  protected def publish(event: E) = dispatch(event, None)

  override def on(ec: ExecutionContext)(subscriber: Subscriber[E])(implicit eventContext: EventContext): Subscription = returning(new StreamSubscription[E](this, subscriber, Some(ec))(WeakReference(eventContext)))(_.enable())

  override def apply(subscriber: Subscriber[E])(implicit eventContext: EventContext): Subscription = returning(new StreamSubscription[E](this, subscriber, None)(WeakReference(eventContext)))(_.enable())

  def foreach(op: E => Unit)(implicit context: EventContext): Unit = apply(op)(context)

  def map[V](f: E => V): EventStream[V] = new MapEventStream[E, V](this, f)
  def flatMap[V](f: E => EventStream[V]): EventStream[V] = new FlatMapLatestEventStream[E, V](this, f)
  def mapAsync[V](f: E => Future[V]): EventStream[V] = new FutureEventStream[E, V](this, f)
  def filter(f: E => Boolean): EventStream[E] = new FilterEventStream[E](this, f)
  def collect[V](pf: PartialFunction[E, V]) = new CollectEventStream[E, V](this, pf)
  def scan[V](zero: V)(f: (V, E) => V): EventStream[V] = new ScanEventStream[E, V](this, zero, f)
  def union(stream: EventStream[E]): EventStream[E] = new UnionEventStream[E](this, stream)

  def next(implicit context: EventContext): CancellableFuture[E] = {
    val p = Promise[E]()
    val o = apply { p.trySuccess(_) }
    p.future.onComplete(_ => o.destroy())(Threading.Background)
    new CancellableFuture(p)
  }

  protected def onWire(): Unit = {}
  protected def onUnwire(): Unit = {}
}

abstract class ProxyEventStream[A, E](sources: EventStream[A]*) extends EventStream[E] with EventListener[A] {
  override protected def onWire() = sources foreach (_.subscribe(this))
  override protected[events] def onUnwire() = sources foreach (_.unsubscribe(this))
}

class MapEventStream[E, V](source: EventStream[E], f: E => V) extends ProxyEventStream[E, V](source) {
  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit = dispatch(f(event), sourceContext)
}

class FlatMapLatestEventStream[E, V](source: EventStream[E], f: E => EventStream[V]) extends EventStream[V] with EventListener[E] {
  @volatile private var mapped: Option[EventStream[V]] = None

  private val mappedListener = new EventListener[V] {
    override protected[events] def onEvent(event: V, currentContext: Option[ExecutionContext]): Unit = {
      dispatch(event, currentContext)
    }
  }

  override protected[events] def onEvent(event: E, currentContext: Option[ExecutionContext]): Unit = {
    mapped.foreach(_.unsubscribe(mappedListener))
    mapped = Some(returning(f(event))(_.subscribe(mappedListener)))
  }

  override protected def onWire(): Unit = {
    source.subscribe(this)
  }

  override protected def onUnwire(): Unit = {
    mapped.foreach(_.unsubscribe(mappedListener))
    mapped = None
    source.unsubscribe(this)
  }
}

class FutureEventStream[E, V](source: EventStream[E], f: E => Future[V])
  extends ProxyEventStream[E, V](source) with DerivedLogTag {

  private val key = randomUUID()

  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit =
    Serialized.future(key)(f(event).andThen {
      case Success(v) => dispatch(v, sourceContext)
      case Failure(t: NoSuchElementException) => // do nothing to allow Future.filter/collect
      case Failure(t) => error(l"async map failed", t)
    }(sourceContext.orElse(executionContext).getOrElse(Threading.Background)))
}

class CollectEventStream[E, V](source: EventStream[E], pf: PartialFunction[E, V]) extends ProxyEventStream[E, V](source) {
  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit =
    if (pf.isDefinedAt(event)) dispatch(pf(event), sourceContext)
}

class FilterEventStream[E](source: EventStream[E], f: E => Boolean) extends ProxyEventStream[E, E](source) {
  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit = if (f(event)) dispatch(event, sourceContext)
}

class UnionEventStream[E](sources: EventStream[E]*) extends ProxyEventStream[E, E](sources: _*) {
  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit = dispatch(event, sourceContext)
}

class ScanEventStream[E, V](source: EventStream[E], zero: V, f: (V, E) => V) extends ProxyEventStream[E, V] {
  @volatile private var value = zero

  override protected[events] def onEvent(event: E, sourceContext: Option[ExecutionContext]): Unit = {
    value = f(value, event)
    dispatch(value, sourceContext)
  }
}
