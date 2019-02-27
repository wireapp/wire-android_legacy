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

import com.waz.log.ZLog2.SafeToLog

sealed trait Availability extends SafeToLog { val id: Int }

object Availability {
  case object None      extends Availability { override val id = 0 }
  case object Available extends Availability { override val id = 1 }
  case object Away      extends Availability { override val id = 2 }
  case object Busy      extends Availability { override val id = 3 }

  val all = List(None, Available, Away, Busy)

  def apply(id: Int): Availability = all.find(_.id == id).getOrElse(throw new IllegalArgumentException(s"Invalid availability id: $id"))
}
