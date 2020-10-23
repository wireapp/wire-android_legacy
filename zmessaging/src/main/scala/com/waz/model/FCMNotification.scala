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
package com.waz.model
import com.waz.utils.Identifiable
import org.threeten.bp.Instant

/**
  * @param stageStartTime instant the push notification was received at stage
  * @param stage the stage the notification was in at time `receivedAt`
  */
case class FCMNotification(override val id: Uid, stage: String, stageStartTime: Instant) extends Identifiable[Uid]

object FCMNotification {
  val Pushed                  = "pushed"
  val Fetched                 = "fetched"
  val StartedPipeline         = "startedPipeline"
  val FinishedPipeline        = "finishedPipeline"
  val everyStage: Seq[String] = Seq(Pushed, Fetched, StartedPipeline, FinishedPipeline)

  def prevStage(stage: String): Option[String] = stage match {
    case FinishedPipeline => Some(StartedPipeline)
    case StartedPipeline  => Some(Fetched)
    case Fetched          => Some(Pushed)
    case _                => None
  }
}
