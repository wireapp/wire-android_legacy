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
package com.waz.zclient.controllers.context

import com.waz.ZLog.{LogTag, debug, logTagFor}
import com.waz.model.ConvId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.global.KeyboardController
import com.waz.zclient.{Injectable, Injector}

class ScrollController(implicit inj: Injector) extends Injectable {

  import ScrollController._

  val zms = inject[Signal[ZMessaging]]
  val keyboardVisibility = inject[KeyboardController].keyboardVisibility

  private var currentSelectedConv = Option.empty[ConvId]
  val selectedConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }

  val msgCursor = Signal(zms, selectedConv).flatMap {
    case (z, convId) => z.messagesStorage.getEntries(convId)
  }

  val lastReadIndex = msgCursor.map(_.lastReadIndex)

  val messagesCount = Signal(zms, selectedConv).flatMap {
    case (z, id) => z.messagesStorage.getEntries(id)
  }.map(_.size)

  val lastVisibleItemIndex = Signal(-1)

  val scrollPosition = for {
    count <- messagesCount
    lr <- lastReadIndex
    kB <- keyboardVisibility
    lv <- lastVisibleItemIndex
  } yield {
    debug(s"Last read: $lr, last visible: $lv, total count: $count")
    if      (false)                 count - 1 //TODO when cursor is clicked => count - 1
    else if (kB)                    count - 1 //keyboard came up, user must have clicked on cursor
    else if (lr == count - 2)       count - 1 //TODO message sent by user should get marked as read
    else if (lv == count - 2)       count - 1 //other user just sent a message while you were scrolled to bottom
    else                            lr
    //TODO stop scrolling to bottom if scrolled up 
  }
}

object ScrollController {
  implicit val logTag: LogTag = logTagFor[ScrollController]
}
