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

import java.util.concurrent.atomic.AtomicBoolean

import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.threading.CancellableFuture.delayed
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events.Events.Subscriber

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.ref.WeakReference
import scala.util.{Failure, Success}

object Signal {
  def apply[A]() = new SourceSignal[A] with NoAutowiring
  def apply[A](e: A) = new SourceSignal[A](Some(e)) with NoAutowiring
  def empty[A]: Signal[A] = new ConstSignal[A](None)
  def const[A](v: A): Signal[A] = new ConstSignal[A](Some(v))
  def apply[A, B](s1: Signal[A], s2: Signal[B]): Signal[(A, B)] = new Zip2Signal[A ,B](s1, s2)
  def apply[A, B, C](s1: Signal[A], s2: Signal[B], s3: Signal[C]): Signal[(A, B, C)] = new Zip3Signal(s1, s2, s3)
  def apply[A, B, C, D](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D]): Signal[(A, B, C, D)] = new Zip4Signal(s1, s2, s3, s4)
  def apply[A, B, C, D, E](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D], s5: Signal[E]): Signal[(A, B, C, D, E)] = new Zip5Signal(s1, s2, s3, s4, s5)
  def apply[A, B, C, D, E, F](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D], s5: Signal[E], s6: Signal[F]): Signal[(A, B, C, D, E, F)] = new Zip6Signal(s1, s2, s3, s4, s5, s6)

  def throttled[A](s: Signal[A], delay: FiniteDuration): Signal[A] = new ThrottlingSignal(s, delay)

  def mix[A](sources: Signal[_]*)(f: => Option[A]): Signal[A] = new ProxySignal[A](sources: _*) {
    override protected def computeValue(current: Option[A]): Option[A] = f
  }
  def foldLeft[A, B](sources: Signal[A]*)(v: B)(f: (B, A) => B): Signal[B] = new FoldLeftSignal[A,B](sources: _*)(v)(f)
  def and(sources: Signal[Boolean]*): Signal[Boolean] = new FoldLeftSignal[Boolean, Boolean](sources: _*)(true)(_ && _)
  def or(sources: Signal[Boolean]*): Signal[Boolean] = new FoldLeftSignal[Boolean, Boolean](sources: _*)(false)(_ || _)

  def sequence[A](sources: Signal[A]*): Signal[Seq[A]] = new ProxySignal[Seq[A]](sources: _*) {
    override protected def computeValue(current: Option[Seq[A]]): Option[Seq[A]] = {
      val res = sources map { _.value }
      if (res.exists(_.isEmpty)) None else Some(res.flatten)
    }
  }

  def future[A](future: Future[A]): Signal[A] = returning(new Signal[A]) { signal =>
    future.onSuccess {
      case res => signal.set(Option(res), Some(Threading.Background))
    } (Threading.Background)
  }

  def wrap[A](initial: A, source: EventStream[A]): Signal[A] = new Signal[A](Some(initial)) {
    lazy val subscription = source { publish } (EventContext.Global)
    override protected def onWire(): Unit = subscription.enable()
    override protected def onUnwire(): Unit = subscription.disable()
  }

  def wrap[A](source: EventStream[A]): Signal[A] = new Signal[A](None) {
    lazy val subscription = source { publish } (EventContext.Global)
    override protected def onWire(): Unit = subscription.enable()
    override protected def onUnwire(): Unit = subscription.disable()
  }
}

class SourceSignal[A](v: Option[A] = None) extends Signal(v) {
  def ! (value: A) = publish(value)
  override def publish(value: A, currentContext: ExecutionContext): Unit = super.publish(value, currentContext)
  def mutate(f: A => A): Boolean = update(_.map(f))
  def mutate(f: A => A, currentContext: ExecutionContext): Boolean = update(_.map(f), Some(currentContext))
  def mutateOrDefault(f: A => A, default: A): Boolean = update(_.map(f).orElse(Some(default)))
}

trait SignalListener {
  // 'currentContext' is the context this method IS run in, NOT the context any subsequent methods SHOULD run in
  def changed(currentContext: Option[ExecutionContext]): Unit
}

class Signal[A](@volatile protected[events] var value: Option[A] = None) extends Observable[SignalListener] with EventSource[A] { self =>
  private object updateMonitor

  protected[events] def update(f: Option[A] => Option[A], currentContext: Option[ExecutionContext] = None): Boolean = {
    val changed = updateMonitor.synchronized {
      val next = f(value)
      if (value != next) { value = next; true }
      else false
    }
    if (changed) notifyListeners(currentContext)
    changed
  }

  protected[events] def set(v: Option[A], currentContext: Option[ExecutionContext] = None) = {
    if (value != v) {
      value = v
      notifyListeners(currentContext)
    }
  }

  private[events] def notifyListeners(currentContext: Option[ExecutionContext]): Unit = super.notifyListeners { _.changed(currentContext) }

  final def currentValue(implicit logTag: LogTag): Option[A] = {
    if (!wired) {
      val prev = value
      disableAutowiring()
//      warn(l"Accessing value of unwired signal: $this, autowiring has been disabled, value was: $prev, is now: $value")(logTag)
    }
    value
  }

  lazy val onChanged: EventStream[A] = new EventStream[A] with SignalListener { stream =>
    private var prev = self.value

    override def changed(ec: Option[ExecutionContext]): Unit = stream.synchronized {
      self.value foreach { current =>
        if (!prev.contains(current)) {
          dispatch(current, ec)
          prev = Some(current)
        }
      }
    }

    override protected def onWire(): Unit = self.subscribe(this)
    override protected[events] def onUnwire(): Unit = self.unsubscribe(this)
  }

  def head(implicit logTag: LogTag): Future[A] = currentValue match {
    case Some(v) => CancellableFuture successful v
    case None =>
      val p = Promise[A]()
      val listener = new SignalListener {
        override def changed(ec: Option[ExecutionContext]): Unit = value foreach p.trySuccess
      }
      subscribe(listener)
      p.future.onComplete(_ => unsubscribe(listener))(Threading.Background)
      value foreach p.trySuccess
      p.future
  }

  def zip[B](s: Signal[B]): Signal[(A, B)] = new Zip2Signal[A, B](this, s)
  def map[B](f: A => B): Signal[B] = new MapSignal[A, B](this, f)
  def filter(f: A => Boolean): Signal[A] = new FilterSignal(this, f)
  def withFilter(f: A => Boolean): Signal[A] = new FilterSignal(this, f)
  def ifTrue(implicit ev: A =:= Boolean): Signal[Unit] = collect { case true => () }
  def ifFalse(implicit ev: A =:= Boolean): Signal[Unit] = collect { case false => () }
  def collect[B](pf: PartialFunction[A, B]): Signal[B] = new ProxySignal[B](this) {
    override protected def computeValue(current: Option[B]): Option[B] = self.value flatMap { v =>
      pf.andThen(Some(_)).applyOrElse(v, { _: A => None })
    }
  }
  def collectValues[B](implicit ev: A =:= Option[B]): Signal[B] = collect { case Some(value: B) => value }
  def foreach(f: A => Unit)(implicit eventContext: EventContext): Subscription = apply(f)
  def flatMap[B](f: A => Signal[B]): Signal[B] = new FlatMapSignal[A, B](this, f)
  def flatten[B](implicit evidence: A <:< Signal[B]): Signal[B] = flatMap(x => x)
  def scan[B](zero: B)(f: (B, A) => B): Signal[B] = new ScanSignal[A, B](this, zero, f)
  def combine[B, C](s: Signal[B])(f: (A, B) => C): Signal[C] = new ProxySignal[C](this, s) {
    override protected def computeValue(current: Option[C]): Option[C] = for (a <- self.value; b <- s.value) yield f(a, b)
  }
  def throttle(delay: FiniteDuration): Signal[A] = new ThrottlingSignal(this, delay)
  def orElse(fallback: Signal[A]): Signal[A] = new ProxySignal[A](self, fallback) {
    override protected def computeValue(current: Option[A]): Option[A] = self.value.orElse(fallback.value)
  }
  def either[B](right: Signal[B]): Signal[Either[A, B]] = map(Left(_): Either[A, B]).orElse(right.map(Right.apply))
  def pipeTo(sourceSignal: SourceSignal[A])(implicit ec: EventContext): Unit = foreach(sourceSignal ! _)

  def onPartialUpdate[B](select: A => B): Signal[A] = new PartialUpdateSignal[A, B](this)(select)

  /** If this signal is computed from sources that change their value via a side effect (such as signals) and is not
    * informed of those changes while unwired (e.g. because this signal removes itself from the sources' children
    * lists in #onUnwire), it is mandatory to update/recompute this signal's value from the sources in #onWire, since
    * a dispatch always happens after #onWire. This is true even if the source values themselves did not change, for the
    * recomputation in itself may rely on side effects (e.g. ZMessaging => SomeValueFromTheDatabase).
    *
    * This also implies that a signal should never #dispatch in #onWire because that will happen anyway immediately
    * afterwards in #subscribe.
    */
  protected def onWire(): Unit = ()
  protected def onUnwire(): Unit = ()

  override def on(ec: ExecutionContext)(subscriber: Subscriber[A])(implicit eventContext: EventContext): Subscription = returning(new SignalSubscription[A](this, subscriber, Some(ec))(WeakReference(eventContext)))(_.enable())
  override def apply(subscriber: Subscriber[A])(implicit eventContext: EventContext): Subscription = returning(new SignalSubscription[A](this, subscriber, None)(WeakReference(eventContext)))(_.enable())

  protected def publish(value: A): Unit = set(Some(value))
  protected def publish(value: A, currentContext: ExecutionContext): Unit = set(Some(value), Some(currentContext))
}

trait NoAutowiring { self: Signal[_] =>
  disableAutowiring()
}

/**
 * Immutable signal value. Can be used whenever some constant or empty signal is needed.
 * Using immutable signals in flatMap chains should have better performance compared to regular signals with the same value.
 */
final class ConstSignal[A](v: Option[A]) extends Signal[A](v) with NoAutowiring {
  override def subscribe(l: SignalListener): Unit = ()
  override def unsubscribe(l: SignalListener): Unit = ()
  override protected[events] def update(f: (Option[A]) => Option[A], ec: Option[ExecutionContext]): Boolean = throw new UnsupportedOperationException("Const signal can not be updated")
  override protected[events] def set(v: Option[A], ec: Option[ExecutionContext]): Unit = throw new UnsupportedOperationException("Const signal can not be changed")
}

final class ThrottlingSignal[A](source: Signal[A], delay: FiniteDuration) extends ProxySignal[A](source) {
  import scala.concurrent.duration._
  private val waiting = new AtomicBoolean(false)
  @volatile private var lastDispatched = 0L

  override protected def computeValue(current: Option[A]): Option[A] = source.value

  override private[events] def notifyListeners(ec: Option[ExecutionContext]): Unit =
    if (waiting.compareAndSet(false, true)) {
      val context = ec.getOrElse(Threading.Background)
      val d = math.max(0, lastDispatched - System.currentTimeMillis() + delay.toMillis)
      delayed(d.millis) {
        lastDispatched = System.currentTimeMillis()
        waiting.set(false)
        super.notifyListeners(Some(context))
      } (context)
    }
}

class FlatMapSignal[A, B](source: Signal[A], f: A => Signal[B]) extends Signal[B] with SignalListener {
  private val Empty = Signal.empty[B]

  private object wiringMonitor
  private var sourceValue: Option[A] = None
  private var mapped: Signal[B] = Empty

  private val sourceListener = new SignalListener {
    override def changed(currentContext: Option[ExecutionContext]): Unit = {
      val changed = wiringMonitor synchronized { // XXX: is this synchronization needed, is it enough? What if we just got unwired ?
        val next = source.value
        if (sourceValue != next) {
          sourceValue = next

          mapped.unsubscribe(FlatMapSignal.this)
          mapped = next.map(f).getOrElse(Empty)
          mapped.subscribe(FlatMapSignal.this)
          true
        } else false
      }

      if (changed) set(mapped.value)
    }
  }

  override def onWire(): Unit = wiringMonitor.synchronized {
    source.subscribe(sourceListener)

    val next = source.value
    if (sourceValue != next) {
      sourceValue = next
      mapped = next.map(f).getOrElse(Empty)
    }

    mapped.subscribe(this)
    value = mapped.value
  }

  override def onUnwire(): Unit = wiringMonitor.synchronized {
    source.unsubscribe(sourceListener)
    mapped.unsubscribe(this)
  }

  override def changed(currentContext: Option[ExecutionContext]): Unit = set(mapped.value, currentContext)
}

abstract class ProxySignal[A](sources: Signal[_]*) extends Signal[A] with SignalListener {
  override def onWire(): Unit = {
    sources foreach (_.subscribe(this))
    value = computeValue(value)
  }

  override def onUnwire(): Unit = sources foreach (_.unsubscribe(this))

  override def changed(ec: Option[ExecutionContext]): Unit = update(computeValue, ec)

  protected def computeValue(current: Option[A]): Option[A]
}

class ScanSignal[A, B](source: Signal[A], zero: B, f: (B, A) => B) extends ProxySignal[B](source) {
  value = Some(zero)

  override protected def computeValue(current: Option[B]): Option[B] =
    source.value map { v => f(current.getOrElse(zero), v) } orElse current
}

class FilterSignal[A](source: Signal[A], f: A => Boolean) extends ProxySignal[A](source) {
  override protected def computeValue(current: Option[A]): Option[A] = source.value.filter(f)
}

class MapSignal[A, B](source: Signal[A], f: A => B) extends ProxySignal[B](source) {
  override protected def computeValue(current: Option[B]): Option[B] = source.value map f
}

class Zip2Signal[A, B](s1: Signal[A], s2: Signal[B]) extends ProxySignal[(A, B)](s1, s2) {
  override protected def computeValue(current: Option[(A, B)]): Option[(A, B)] = for (a <- s1.value; b <- s2.value) yield (a, b)
}

class Zip3Signal[A, B, C](s1: Signal[A], s2: Signal[B], s3: Signal[C]) extends ProxySignal[(A, B, C)](s1, s2, s3) {
  override protected def computeValue(current: Option[(A, B, C)]): Option[(A, B, C)] = for {
    a <- s1.value
    b <- s2.value
    c <- s3.value
  } yield (a, b, c)
}

class Zip4Signal[A, B, C, D](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D]) extends ProxySignal[(A, B, C, D)](s1, s2, s3, s4) {
  override protected def computeValue(current: Option[(A, B, C, D)]): Option[(A, B, C, D)] = for {
    a <- s1.value
    b <- s2.value
    c <- s3.value
    d <- s4.value
  } yield (a, b, c, d)
}

class Zip5Signal[A, B, C, D, E](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D], s5: Signal[E]) extends ProxySignal[(A, B, C, D, E)](s1, s2, s3, s4, s5) {
  override protected def computeValue(current: Option[(A, B, C, D, E)]): Option[(A, B, C, D, E)] = for {
    a <- s1.value
    b <- s2.value
    c <- s3.value
    d <- s4.value
    e <- s5.value
  } yield (a, b, c, d, e)
}

class Zip6Signal[A, B, C, D, E, F](s1: Signal[A], s2: Signal[B], s3: Signal[C], s4: Signal[D], s5: Signal[E], s6: Signal[F]) extends ProxySignal[(A, B, C, D, E, F)](s1, s2, s3, s4, s5, s6) {
  override protected def computeValue(current: Option[(A, B, C, D, E, F)]): Option[(A, B, C, D, E, F)] = for {
    a <- s1.value
    b <- s2.value
    c <- s3.value
    d <- s4.value
    e <- s5.value
    f <- s6.value
  } yield (a, b, c, d, e, f)
}

class FoldLeftSignal[A, B](sources: Signal[A]*)(v: B)(f: (B, A) => B) extends ProxySignal[B](sources: _*) {
  override protected def computeValue(current: Option[B]): Option[B] =
    sources.foldLeft(Option(v))((mv, signal) => for (a <- mv; b <- signal.value) yield f(a, b))
}

class RefreshingSignal[A](loader: => CancellableFuture[A], refreshEvent: EventStream[_]) extends Signal[A] {
  import RefreshingSignal._
  private val queue = new SerialDispatchQueue(name = "RefreshingSignal")

  @volatile private var loadFuture = CancellableFuture.cancelled[Unit]()
  @volatile private var subscription = Option.empty[Subscription]

  private def reload() = subscription.foreach { _ =>
    loadFuture.cancel()(tag)
    val p = Promise[Unit]
    val thisReload = CancellableFuture.lift(p.future)
    loadFuture = thisReload

    loader.onComplete {
      case Success(v) if loadFuture eq thisReload =>
        p.success(set(Some(v), Some(Threading.Background)))
      case Failure(ex) if loadFuture eq thisReload =>
        import com.waz.log.LogSE._
        error(l"Error while loading RefreshingSignal", ex)
        p.failure(ex)
      case _ =>
    }(queue)
  }

  override protected def onWire(): Unit = {
    super.onWire()
    Future {
      subscription = Some(refreshEvent.on(queue)(_ => reload())(EventContext.Global))
      reload()
    }(queue)
  }

  override protected def onUnwire(): Unit = {
    super.onUnwire()
    Future {
      subscription.foreach(_.unsubscribe())
      subscription = None
      loadFuture.cancel()(tag)
      value = None
    }(queue)
  }
}

class PartialUpdateSignal[A, B](source: Signal[A])(select: A => B) extends ProxySignal[A](source) {

  private object updateMonitor

  override protected[events] def update(f: Option[A] => Option[A], currentContext: Option[ExecutionContext]) = {
    val changed = updateMonitor.synchronized {
      val next = f(value)
      if (value.map(select) != next.map(select)) {
        value = next; true
      }
      else false
    }
    if (changed) notifyListeners(currentContext)
    changed
  }

  override protected def computeValue(current: Option[A]) = source.value
}


object RefreshingSignal {
  private implicit val tag: LogTag = LogTag("RefreshingSignal")

  def apply[A](loader: => Future[A], refreshEvent: EventStream[_]): RefreshingSignal[A] = new RefreshingSignal(CancellableFuture.lift(loader), refreshEvent)
}

case class ButtonSignal[A](service: Signal[A], buttonState: Signal[Boolean])(onClick: (A, Boolean) => Unit) extends ProxySignal[Boolean](service, buttonState) {

  def press()(implicit logTag: LogTag): Unit = if (wired) {
    (service.value, buttonState.value) match {
      case (Some(s), Some(b)) => onClick(s, b)
      case _ => warn(l"ButtonSignal is empty")
    }
  } else warn(l"ButtonSignal not wired")

  override protected def computeValue(current: Option[Boolean]) = buttonState.value
}
