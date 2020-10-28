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
import com.waz.model.ConvId
import com.waz.model.sync.SyncJob.Priority
import com.wire.signals.SerialDispatchQueue

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
    while (queue.nonEmpty) {
      val handle = queue.dequeue()
      if (!handle.isCompleted) {
        if (handle.priority > nextJobMinPriority) {
          queue.enqueue(handle)
          return //TODO remove return
        }

        if (handle.promise.trySuccess(())) runningJobs += 1
        else queue.enqueue(handle)
      }
    }
  }

  def acquire(priority: Int): Future[Unit] = {
    verbose(l"acquire($priority), running: $runningJobs")
    val handle = new PriorityHandle(priority)
    Future {
      queue += handle
      processQueue()
    }
    handle.future
  }

  def release(): Unit = Future {
    verbose(l"release, running: $runningJobs")
    runningJobs -= 1
    processQueue()
  }

  private def processConvQueue(): Unit = {
    convQueue = convQueue.filter(!_.isCompleted)
    convQueue foreach { handle =>
      if (!convs(handle.convId) && handle.promise.trySuccess(new ConvLock(handle.convId, this))) convs += handle.convId
    }
  }

  def acquire(res: ConvId): Future[ConvLock] = {
    verbose(l"acquire($res)")
    val handle = new ConvHandle(res)
    Future {
      convQueue :+= handle
      processConvQueue()
    }
    handle.future
  }

  def release(r: ConvId): Unit = Future {
    verbose(l"release($r)")
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

  case class PriorityHandle(priority: Int) extends WaitHandle[Unit] {

    override def equals(o: scala.Any): Boolean = o match {
      case h @ PriorityHandle(p) => p == priority && h.id == id
      case _ => false
    }

    override def toString: String = s"PriorityHandle($priority, id: $id, completed: $isCompleted)"
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
  def release() = if (released.compareAndSet(false, true)) queue.release(convId)
}
