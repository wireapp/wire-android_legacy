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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Event, RConvEvent}
import com.waz.service.EventScheduler.Stage
import com.waz.service.EventScheduler.Stage.Composite
import com.waz.log.LogSE._
import com.wire.signals.{DispatchQueue, SerialDispatchQueue}

import scala.concurrent.Future
import scala.util.Try

class EventPipeline(scheduler: => EventScheduler) extends (Seq[Event] => Future[Unit]) with DerivedLogTag {
  private implicit val serialDispatcher: DispatchQueue = SerialDispatchQueue()

  private def unrollStages(stage: Stage): Seq[Stage.Atomic] = stage match {
    case Composite(_, stages) => stages.flatMap(unrollStages)
    case stage: Stage.Atomic  => Seq(stage)
    case _                    => Nil
  }

  private lazy val stages: Seq[Stage.Atomic] = unrollStages(scheduler.layout)

  def apply(input: Seq[Event]): Future[Unit] =
    process(input.map((0, _))).map(_ => ())

  def process(input: Seq[(Int, Event)]): Future[Seq[Int]] =
    Future.traverse(input) { case (index, event) =>
      verbose(l"Event going through the pipeline: ${event}")
      val rId = RConvEvent(event)
      val partialResults = stages.filter { e =>
        e.isEligible(event)
      }.map { stage =>
        Try {
          stage(rId, Seq(event)).map(_ => true).recover { case _ =>
            warn(l"Unable to process event DD24 ${event}")
            false
          }
        }.getOrElse {
          warn(l"Unable to process event DD23 ${event}")
          Future.successful(false)
        }
      }

      Future.sequence(partialResults).map { results =>
        if (results.forall(r => r)) {
          Some(index)
        } else {
          warn(l"Unable to process the event: $event (index: $index, class: ${event.getClass.getName})")
          None
        }
      }
    }.map(_.flatten.toVector)
}
