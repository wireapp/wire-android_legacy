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
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.global.KeyboardController
import com.waz.zclient.{Injectable, Injector, WireContext}
import timber.log.Timber

class ScrollController(implicit cxt: WireContext, inj: Injector) extends Injectable {

  implicit val eventContext = cxt.eventContext

  import ScrollController._

  val keyboardVisibility = inject[KeyboardController].keyboardVisibility
  val adapter = Signal[ScrollAdapter]
  val listHeight = Signal[Int]

  val lastReadIndex = adapter.flatMap(_.lastReadIndex)

  val lastMessageIndex = adapter.flatMap(_.msgCount).map(_ - 1)

  val lastVisibleItemIndex = Signal(-1)

  val selectedConv = adapter.flatMap(_.selectedConversation)

  val scrolledToBottom = lastVisibleItemIndex.zip(lastMessageIndex).map {
    case (lv, lm) if lv == lm => true
    case _ => false
  }

  @volatile private var currentScrollPos = -1
  private var currentConv = Option.empty[ConvId]

  val scrollPosition = for {
    conv <- selectedConv
    sB <- scrolledToBottom
    lm <- lastMessageIndex
    lr <- lastReadIndex
  } yield {
    Timber.d(s"ScrolledToBottom?: $sB, total count: $lm")
    if (sB) lm //keep scrolled to bottom
    else {
      if (!currentConv.contains(conv)) {
        currentConv = Some(conv)
        lr
      }
      else currentScrollPos
    }
  }
  scrollPosition (currentScrollPos = _)
}

trait ScrollAdapter {
  def initialLastReadIndex: Signal[(ConvId, Int)]
  def msgCount: Signal[Int]
}

object ScrollController {
  implicit val logTag: LogTag = logTagFor[ScrollController]
}
