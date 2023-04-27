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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.model.Event
import com.wire.signals.{CancellableFuture, SerialDispatchQueue, Serialized}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait EventProcessingQueue[A <: Event] {

  protected implicit val evClassTag: ClassTag[A]
  protected val selector: A => Boolean = { _ => true }

  def enqueue(event: A): Future[Any]

  def enqueue(events: Seq[A]): Future[Any]

  def enqueueEvent(event: Event): Future[Any] = event match {
    case ev: A if selector(ev) => enqueue(ev)
    case _ => Future.successful(()) // ignore
  }

  def enqueueEvents(events: Seq[Event]): Future[Any] = enqueue(events collect { case ev: A if selector(ev) => ev })
}

object EventProcessingQueue {

  def apply[A <: Event : ClassTag, B](eventProcessor: A => Future[B]) = {
    val classTag = implicitly[ClassTag[A]]

    new EventProcessingQueue[A] {
      import com.waz.threading.Threading.Implicits.Background
      override protected implicit val evClassTag = classTag
      override def enqueue(event: A): Future[Any] = eventProcessor(event)
      override def enqueue(events: Seq[A]): Future[Any] = Future.traverse(events)(eventProcessor)
    }
  }
}

class SerialEventProcessingQueue[A <: Event](processor: Seq[A] => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A])
  extends SerialProcessingQueue[A](processor, name) with EventProcessingQueue[A]

class GroupedEventProcessingQueue[A <: Event, Key]
  (groupBy: A => Key, processor: (Key, Seq[A]) => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A])
  extends EventProcessingQueue[A] {

  private implicit val dispatcher = SerialDispatchQueue(name = s"GroupedEventProcessingQueue[${evClassTag.runtimeClass.getSimpleName}]")

  private val queues = new mutable.HashMap[Key, SerialProcessingQueue[A]]

  private def queue(key: Key) =
    queues.getOrElseUpdate(key, new SerialProcessingQueue[A](processor(key, _), s"${name}_$key"))

  override def enqueue(event: A): Future[Any] = Future(queue(groupBy(event))).flatMap(_.enqueue(event))

  override def enqueue(events: Seq[A]): Future[Vector[Any]] =
    Future.traverse(events.groupBy(groupBy).toVector) {
      case (key, es) => Future(queue(key)).flatMap(_.enqueue(es))
    }

  def post[T](k: Key)(task: => Future[T]): Future[T] = Future {
    queue(k).post(task)
  }.flatMap(identity)
}

class SerialProcessingQueue[A](processor: Seq[A] => Future[Any], name: String = "") {
  private implicit val logTag: LogTag = LogTag(name)

  private val queue = new ConcurrentLinkedQueue[A]()

  def enqueue(event: A): Future[Any] = {
    queue.offer(event)
    processQueue()
  }

  def !(event: A): Future[Any] = enqueue(event)

  def enqueue(events: Seq[A]): Future[Any] = if (events.nonEmpty) {
    events.foreach(queue.offer)
    processQueue()
  } else
    Future.successful(())

  protected def processQueue(): Future[Any] = {
    verbose(l"processQueue (queue: $this)")
    post(processQueueNow())
  }

  private final def fromTry[T](f: => Future[T]): Future[T] = Try(f) match {
    case Success(value) => {
      verbose(l"SSM5 fromTry -> success")
      value
    }
    case Failure(ex)    => {
      verbose(l"SSM5 fromTry -> failure")
      Future.failed(ex)
    }
  }

  protected def processQueueNow(): Future[Any] = {
    val events = Iterator.continually(queue.poll()).takeWhile(_ != null).toVector
    verbose(l"processQueueNow (queue: $this), events: $events")
    if (events.nonEmpty) {
      verbose(l"SSM5 queue is not empty, fromTry")
      fromTry(processor(events)).recoverWithLog()
    }
    else {
      verbose(l"SSM5 queue is empty")
      Future.successful(())
    }
  }

  // post some task on this queue, effectively blocking all other processing while this task executes
  def post[T](f: => Future[T]): Future[T] = Serialized.future(name)(fromTry(f))

  /* just for tests! */
  def clear(): Unit = queue.clear()
}

class ThrottledProcessingQueue[A](delay: FiniteDuration, processor: Seq[A] => Future[Any], name: String = "") extends SerialProcessingQueue[A](processor, name)  {
  private implicit val logTag: LogTag = LogTag(name)
  private implicit val dispatcher = SerialDispatchQueue(name = if (name.isEmpty) "ThrottledProcessingQueue_" + hashCode() else name)
  private val waiting = new AtomicBoolean(false)
  @volatile private var waitFuture: CancellableFuture[Any] = CancellableFuture.successful(())
  private var lastDispatched = 0L

  override protected def processQueue(): Future[Any] = {
    val tag = UUID.randomUUID()
    verbose(l"SSM2<TAG:$tag> processQueue (throttled), preparing to wait...")
    if (waiting.compareAndSet(false, true)) {
      verbose(l"SSM2<TAG:$tag> wait successful")
      post {
        val d = math.max(0, lastDispatched - System.currentTimeMillis() + delay.toMillis)
        verbose(l"SSM2<TAG:$tag> setting delay ${d.millis}ms")
        waitFuture = CancellableFuture.delay(d.millis)
        if (!waiting.get()) waitFuture.cancel() // to avoid race conditions with `flush`
        waitFuture.future.flatMap { _ =>
          verbose(l"SSM2<TAG:$tag> timeout successful, processQueueNow")
          CancellableFuture.lift(processQueueNow())
        } .recover {
          case e: Throwable => {
            verbose(l"SSM2<TAG:$tag> timeout fail, recover from $e")
            waiting.set(false)
          }
        }
      }
    } else {
      verbose(l"SSM2<TAG:$tag> wait NOT successful")
      waitFuture.future
    }
  }


  override protected def processQueueNow(): Future[Any] = {
    waiting.set(false)
    lastDispatched = System.currentTimeMillis()
    super.processQueueNow()
  }

  def flush() = {
    waiting.set(false)
    waitFuture.cancel()
    post {
      processQueueNow()
    }
  }
}
