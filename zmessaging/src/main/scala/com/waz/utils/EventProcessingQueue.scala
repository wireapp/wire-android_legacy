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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
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

  def enqueue(event: A, tag: Option[UUID]): Future[Any]

  def enqueue(events: Seq[A], tag: Option[UUID]): Future[Any]

  def enqueueEvent(event: Event, tag: Option[UUID]): Future[Any] = event match {
    case ev: A if selector(ev) => enqueue(ev, tag)
    case _ => Future.successful(()) // ignore
  }

  def enqueueEvents(events: Seq[Event], tag: Option[UUID]): Future[Any] = enqueue(events collect { case ev: A if selector(ev) => ev }, tag)
}

object EventProcessingQueue {

  def apply[A <: Event : ClassTag, B](eventProcessor: (A, Option[UUID]) => Future[B]) = {
    val classTag = implicitly[ClassTag[A]]

    new EventProcessingQueue[A] {
      import com.waz.threading.Threading.Implicits.Background
      override protected implicit val evClassTag = classTag
      override def enqueue(event: A, tag: Option[UUID]): Future[Any] = eventProcessor(event, tag)
      override def enqueue(events: Seq[A], tag: Option[UUID]): Future[Any] = Future.traverse(events)({ e => eventProcessor(e, tag)})
    }
  }
}

class SerialEventProcessingQueue[A <: Event](processor: (Option[UUID], Seq[A]) => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A])
  extends SerialProcessingQueue[A](processor, name) with EventProcessingQueue[A]

class GroupedEventProcessingQueue[A <: Event, Key]
  (groupBy: A => Key, processor: (Key, Seq[A], Option[UUID]) => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A])
  extends EventProcessingQueue[A] with DerivedLogTag {

  private implicit val dispatcher = SerialDispatchQueue(name = s"GroupedEventProcessingQueue[${evClassTag.runtimeClass.getSimpleName}]")

  private val queues = new mutable.HashMap[Key, SerialProcessingQueue[A]]

  private def queue(key: Key, tag: Option[UUID]) =
    queues.getOrElseUpdate(key, new SerialProcessingQueue[A]({ (t, e) => processor(key, e, t)}, s"${name}_$key"))

  override def enqueue(event: A, tag: Option[UUID]): Future[Any] = Future(queue(groupBy(event), tag)).flatMap(_.enqueue(event, tag))

  override def enqueue(events: Seq[A], tag: Option[UUID]): Future[Vector[Any]] = {
    verbose(l"SSEQ<TAG:$tag> EventProcessingQueue enqueue step 1")
    Future.traverse(events.groupBy(groupBy).toVector) {
      case (key, es) =>
        verbose(l"SSEQ<TAG:$tag> EventProcessingQueue enqueue step 2")
        Future(queue(key, tag)).flatMap({
            verbose(l"SSEQ<TAG:$tag> EventProcessingQueue enqueue step 3")
            _.enqueue(es, tag)
          }
        )
    }
  }

  def post[T](k: Key, tag: Option[UUID])(task: => Future[T]): Future[T] = Future {
    queue(k, tag).post(tag, task)
  }.flatMap(identity)
}

class SerialProcessingQueue[A](processor: (Option[UUID], Seq[A]) => Future[Any], name: String = "") {
  private implicit val logTag: LogTag = LogTag(name)

  private val queue = new ConcurrentLinkedQueue[A]()

  def enqueue(event: A, tag: Option[UUID]): Future[Any] = {
    queue.offer(event)
    processQueue()
  }

  def !(event: A, tag: Option[UUID]): Future[Any] = enqueue(event, tag)

  def enqueue(events: Seq[A], tag: Option[UUID]): Future[Any] =
    if (events.nonEmpty) {
      verbose(l"SSEQ<TAG:$tag> SerialProcessingQueue enqueue step 1")
      events.foreach(queue.offer)
      verbose(l"SSEQ<TAG:$tag> SerialProcessingQueue enqueue step 2")
      val f = processQueue(tag)
      verbose(l"SSEQ<TAG:$tag> SerialProcessingQueue enqueue step 3")
      f
    } else {
      verbose(l"SSEQ<TAG:$tag> SerialProcessingQueue enqueue step 4")
      Future.successful(())
    }
  protected def processQueue(tag: Option[UUID] = None): Future[Any] = {
    verbose(l"processQueue (queue: $this) tag: $tag")
    post(tag, processQueueNow(tag))
  }

  private final def fromTry[T](tag: Option[UUID], f: => Future[T]): Future[T] =
    {
      verbose(l"SSM5<TAG:$tag> fromTry -> trying...")
      Try(f) match {
        case Success(value) => {
          verbose(l"SSM5<TAG:$tag> fromTry -> success")
          value
        }
        case Failure(ex)    => {
          verbose(l"SSM5<TAG:$tag> fromTry -> failure")
          Future.failed(ex)
        }
      }
    }

  protected def processQueueNow(tag: Option[UUID] = None): Future[Any] = {
    verbose(l"SSPQ2<TAG:$tag> processQueueNow (queue: $this) step 1")
    val events = Iterator.continually(queue.poll()).takeWhile(_ != null).toVector
    verbose(l"SSPQ2<TAG:$tag> processQueueNow (queue: $this), events: $events step 2")
    if (events.nonEmpty) {
      verbose(l"SSPQ2<TAG:$tag> queue is not empty, step 3. Processor is ${processor}")
      fromTry(tag, processor(tag, events)).recoverWithLog()
    }
    else {
      verbose(l"SSPQ2<TAG:$tag> queue is empty")
      Future.successful(())
    }
  }

  // post some task on this queue, effectively blocking all other processing while this task executes
  def post[T](tag: Option[UUID], f: => Future[T]): Future[T] = {
    verbose(l"SSPQ3<TAG:$tag> post on the queue")
    Serialized.future(name)(fromTry(tag, f))
  }

  /* just for tests! */
  def clear(): Unit = queue.clear()
}
