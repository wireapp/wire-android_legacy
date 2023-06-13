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
package com.waz.service

import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{Event, RConvEvent, RConvId}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals.Signal

import java.util.UUID
import scala.collection.breakOut
import scala.concurrent.Future.{successful, traverse}
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

class EventScheduler(layout: EventScheduler.Stage) extends DerivedLogTag {
  import Threading.Implicits.Background

  import EventScheduler._

  private val queue = new SerialEventProcessingQueue[Event]((e) => {
      Future.sequence(
        e.map { event =>
        executeSchedule(RConvEvent(event), createSchedule(e))
      }
    ).map { _ => Unit }
  }, "EventScheduler")

  val currentEventTag = Signal[Option[UUID]]()

  private def executeSchedule(conv: RConvId, schedule: Schedule): Future[Unit] = {

    def dfs(s: Stream[Schedule]): Future[Unit] = s match {
      case Stream.Empty =>
        successful(())

      case NOP #:: remaining =>
        dfs(remaining)

      case Leaf(stage, events) #:: remaining =>
        val p = Promise[Unit]()
        val tag = UUID.randomUUID()
        currentEventTag ! Option(tag)
        stage(conv, events).onComplete { result =>
          result match {
            case _ => p.completeWith(dfs(remaining))
          }
          currentEventTag ! None
        }
        p.future

      case Branch(Sequential, schedules) #:: remaining =>
        dfs(schedules #::: remaining)

      case Branch(Parallel, schedules) #:: remaining =>
        val p = Promise[Unit]()
        traverse(schedules)(s => dfs(Stream(s))).onComplete {
          case _ => p.completeWith(dfs(remaining))
        }
        p.future
    }

    dfs(Stream(schedule))
  }

  def enqueue(events: Traversable[Event]): Future[Unit] = queue.enqueue(events.to[Vector]).recoverWithLog()

//  def post[A](conv: RConvId)(task: => Future[A]) = queue.post(conv)(task) // TODO this is rather hacky; maybe it could be replaced with a kind of "internal" event, i.e. events caused by events

  def createSchedule(events: Traversable[Event]): Schedule = schedule(layout, events.toStream)

  private def schedule(stage: Stage, events: Stream[Event]): Schedule = {
    val eligible = events.filter(stage.isEligible)

    val logTag: LogTag = stage match {
      case s: Stage.Atomic => s.eventTag
      case Stage.Composite(strat: Strategy, _) => LogTag(strat.getClass.getSimpleName)
    }
    verbose(l"scheduling ${eligible.size} eligible events from total ${events.size}")(LogTag(s"${LogTag[Stage].value}[${logTag.value}]"))

    if (eligible.isEmpty) NOP else stage match {
      case s: Stage.Atomic =>
        Leaf(s, eligible.toVector)

      case Stage.Composite(strat: ExecutionStrategy, stages) =>
        Branch(strat, stages.toStream.map(s => schedule(s, eligible)))

      case Stage.Composite(Interleaved, stages) =>
        def interleave(es: Stream[Event]): Stream[(Stage, Event)] = es match {
          case Stream.Empty => Stream.Empty
          case event #:: tail => stages.to[Stream].filter(_.isEligible(event)).map(stage => (stage, event)) #::: interleave(tail)
        }

        def compact(stagedEvents: Stream[(Stage, Event)]): Stream[Schedule] = stagedEvents match {
          case Stream.Empty => Stream.Empty
          case (staged@(stage, event)) #:: tail =>
            val (groupable, remaining) = tail.span(_._1 == stage)
            schedule(stage, (staged #:: groupable).map(_._2)(breakOut)) #:: compact(remaining)
        }

        Branch(Sequential, compact(interleave(eligible)))
    }
  }
}

object EventScheduler extends DerivedLogTag {
  sealed trait Strategy

  sealed trait SchedulingStrategy extends Strategy

  sealed trait ExecutionStrategy extends Strategy

  case object Sequential extends SchedulingStrategy with ExecutionStrategy

  case object Parallel extends SchedulingStrategy with ExecutionStrategy

  case object Interleaved extends SchedulingStrategy

  sealed trait Stage {
    def isEligible(e: Event): Boolean
  }

  object Stage {
    case class Composite(strategy: SchedulingStrategy, stages: Vector[Stage]) extends Stage {
      def isEligible(e: Event): Boolean = stages.exists(_.isEligible(e))
    }

    trait Atomic extends Stage {

      val eventTag: LogTag = LogTag("Event")

      def apply(conv: RConvId, es: Traversable[Event]): Future[Any]
    }

    def apply(strategy: SchedulingStrategy)(stages: Stage*): Composite = Composite(strategy, stages.toVector)

    def apply[A <: Event](processor: (RConvId, Vector[A]) => Future[Any], include: A => Boolean = (_: A) => true)(implicit EligibleEvent: ClassTag[A]): Atomic = new Atomic {

      override val eventTag = LogTag(EligibleEvent.runtimeClass.getSimpleName)

      def isEligible(e: Event): Boolean = e match {
        case EligibleEvent(a) if include(a) => true
        case _ => false
      }

      def apply(conv: RConvId, es: Traversable[Event]): Future[Any] = {
        val events: Vector[A] = es.collect { case EligibleEvent(a) if include(a) => a }(breakOut)
        verbose(l"processing ${events.size} ${showString(if (events.size == 1) "event" else "events")}: ${events.take(10)} ${showString(if (events.size > 10) "..." else "")}")(LogTag(s"${LogTag[Stage].value}[${eventTag.value}]"))
        processor(conv, events)
      }
    }
  }

  sealed trait Schedule

  case class Branch(strategy: ExecutionStrategy, schedules: Stream[Schedule]) extends Schedule

  case class Leaf(stage: Stage.Atomic, events: Vector[Event]) extends Schedule

  val NOP = Leaf(Stage[Event]((s, e) => successful(()), _ => false), Vector.empty)

}
