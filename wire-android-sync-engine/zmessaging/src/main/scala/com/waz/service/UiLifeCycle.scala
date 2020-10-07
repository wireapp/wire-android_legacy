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

import com.wire.signals.SerialDispatchQueue
import com.wire.signals.Signal

trait UiLifeCycle {
  //App is in the foregound
  def uiActive: Signal[Boolean]

  def acquireUi(): Unit
  def releaseUi(): Unit
}

class UiLifeCycleImpl extends UiLifeCycle {

  private implicit val dispatcher = SerialDispatchQueue(name = "LifeCycleDispatcher")

  private val uiCount = Signal(0)

  override val uiActive = uiCount.map(_ > 0)

  def acquireUi(): Unit = uiCount.mutate(_ + 1, dispatcher)
  def releaseUi(): Unit = uiCount.mutate({ u =>
    assert(u > 0, "Ui count should be greater than 0 before being released")
    u - 1
  }, dispatcher)

}
