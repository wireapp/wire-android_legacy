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
package com.waz.api.impl

import com.waz.api.ProgressIndicator.State

object ProgressIndicator {
  type Callback = ProgressData => Unit

  case class ProgressData(current: Long, total: Long, state: State)
  object ProgressData {
    val Indefinite = ProgressData(0, -1, State.RUNNING)
    val Unknown = ProgressData(0, 0, State.UNKNOWN)

    def apply(current: Long, total: Option[Long]): ProgressData = {
      (current, total) match {
        case (progress, Some(total)) if progress == total =>
          ProgressData(progress, total, com.waz.api.ProgressIndicator.State.COMPLETED)
        case (progress, Some(total)) =>
          ProgressData(progress, total, com.waz.api.ProgressIndicator.State.RUNNING)
        case (_, None) =>
          ProgressData.Indefinite
      }
    }
  }

  class ProgressReporter(callback: ProgressData => Unit, total: Long) {
    def running(current: Long) = callback(ProgressData(current, total, State.RUNNING))
    def completed() = callback(ProgressData(total, total, State.COMPLETED))
  }
}
