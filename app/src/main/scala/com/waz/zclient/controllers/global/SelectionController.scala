/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.controllers.global

import com.waz.model.MessageId
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._

class SelectionController(implicit injector: Injector, ev: EventContext) extends Injectable {

  private val zms = inject[Signal[ZMessaging]]

  val selectedConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }

  object messages {
    private val selection = Signal(Set.empty[MessageId])

    val focused = Signal(Option.empty[MessageId])

    selectedConv.onChanged { _ => clear() }

    def clear() = {
      selection.mutate(_ => Set.empty)
      focused ! None
    }

    def isSelected(id: MessageId): Signal[Boolean] = selection map { _.contains(id) }

    def setSelected(id: MessageId, selected: Boolean) =
      selection.mutate(s => if (selected) s + id else s - id)

    def toggleFocused(id: MessageId) = {
      verbose(s"toggleFocused($id)")
      focused.mutate {
        case Some(`id`) => None
        case _ => Some(id)
      }
    }
  }
}
