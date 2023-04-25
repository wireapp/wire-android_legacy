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
package com.waz.sync.queue

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{ConvId, SyncId}
import com.waz.model.sync.SyncJob.Priority
import com.wire.signals.SerialDispatchQueue

import java.util.UUID
import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

class SyncSerializer extends DerivedLogTag {
  import SyncSerializer._
  private implicit val dispatcher = SerialDispatchQueue(name = "SyncSerializer")

  private var runningJobs = 0
  private val convs = new mutable.HashSet[ConvId]
  private var convQueue = Queue[ConvHandle]()
  private val queue = new mutable.PriorityQueue[PriorityHandle]()(PriorityHandle.PriorityOrdering.reverse)

  private[sync] def nextJobMinPriority = runningJobs match {
    case 0 => Priority.MinPriority
    case 1 => Priority.Low
    case 2 => Priority.Normal
    case 3 => Priority.High
    case _ => Priority.Critical
  }

  private def processQueue(): Unit = {
    val id = UUID.randomUUID()
    debug(l"SSM1<$id> Processing SyncQueue: size ${queue.size}")
    while (queue.nonEmpty) {
      debug(l"SSM1<$id> Queue is not empty: ${queue.size}")
      val handle = queue.dequeue()
      debug(l"SSM1<$id> Found handle $handle")
      if (!handle.isCompleted) {
        debug(l"SSM1<$id> Handle $handle is not complete, priority ${handle.priority} vs. next job priority ${nextJobMinPriority}")
        if (handle.priority > nextJobMinPriority) { // IF this is not high enough priority, put it back, stop
          debug(l"SSM1<$id> Enqueuing handle due to priority: $handle")
          queue.enqueue(handle)
          return //TODO remove return
        }

        if (handle.promise.trySuccess(())) {
          debug(l"SSM1<$id> Running jobs+1 for $handle")
          runningJobs += 1
        }
        else {
          debug(l"SSM1<$id> Enqueuing handle as promise is not success $handle")
          queue.enqueue(handle)
        }
      }
    }
    debug(l"SSM1 Done processing SyncQueue <$id>")
  }

  def acquire(priority: Int, id: SyncId): Future[Unit] = {
    verbose(l"acquire($priority) for $id, running: $runningJobs")
    val handle = new PriorityHandle(priority, id)
    Future {
      queue += handle
      processQueue()
    }
    handle.future
  }

  def release(id: SyncId): Unit = Future {
    verbose(l"release for $id, running: $runningJobs")
    runningJobs -= 1
    processQueue()
  }

  private def processConvQueue(): Unit = {
    convQueue = convQueue.filter(!_.isCompleted)
    convQueue foreach { handle =>
      if (!convs(handle.convId) && handle.promise.trySuccess(new ConvLock(handle.convId, this))) convs += handle.convId
    }
  }

  def acquire(res: ConvId, id: SyncId): Future[ConvLock] = {
    verbose(l"acquire($res) for $id")
    val handle = new ConvHandle(res)
    Future {
      convQueue :+= handle
      processConvQueue()
    }
    handle.future
  }

  def release(r: ConvId, id: SyncId): Unit = Future {
    verbose(l"release($r) for $id")
    convs -= r
    processConvQueue()
  }
}

object SyncSerializer {
  private val seq = new AtomicLong(0)

  abstract class WaitHandle[A] {
    val id = seq.incrementAndGet()
    val promise = Promise[A]()
    def future = promise.future

    def isCompleted = promise.isCompleted
  }

  case class PriorityHandle(priority: Int, syncId: SyncId) extends WaitHandle[Unit] {

    override def equals(o: scala.Any): Boolean = o match {
      case h @ PriorityHandle(p, _) => p == priority && h.id == id
      case _ => false
    }

    override def toString: String = s"PriorityHandle($priority, sync: $syncId, id: $id, completed: $isCompleted)"
  }

  object PriorityHandle {
    implicit object PriorityOrdering extends Ordering[PriorityHandle] {
      override def compare(x: PriorityHandle, y: PriorityHandle): Int = Ordering.Int.compare(x.priority, y.priority) match {
        case 0 => Ordering.Long.compare(x.id, y.id)
        case res => res
      }
    }
  }

  case class ConvHandle(convId: ConvId) extends WaitHandle[ConvLock]
}

case class ConvLock(convId: ConvId, queue: SyncSerializer) {
  private val released = new AtomicBoolean(false)
  def release(id: SyncId) = if (released.compareAndSet(false, true)) queue.release(convId, id)
}
