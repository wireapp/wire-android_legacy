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
package com.waz.zclient.messages

import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.messages.ScrollController.Scroll

class ScrollController(adapter: MessagesListView.Adapter)(implicit ec: EventContext) {

  val listHeight = Signal[Int]()
  val onScrollToBottomRequested = EventStream[Int]

  private var shouldScrollToBottom = false

  def onScrolled(lastVisiblePosition: Int) = shouldScrollToBottom = lastVisiblePosition == lastPosition

  def onDragging(): Unit = shouldScrollToBottom = false

  private def lastPosition = adapter.getItemCount - 1
  
  val onScroll = EventStream.union(
    adapter.initialLastReadIndex.onChanged.map { case (_, lastReadPosition) => Scroll(lastReadPosition, smooth = false) },
    onScrollToBottomRequested.map(_ => Scroll(lastPosition, smooth = true)),
    listHeight.onChanged.filter(_ => shouldScrollToBottom).map(_ => Scroll(lastPosition, smooth = false)),
    adapter.msgCount.onChanged.filter(_ => shouldScrollToBottom).map(_ => Scroll(lastPosition, smooth = true))
  ) .filter(_.position >= 0)
}

object ScrollController {
  case class Scroll(position: Int, smooth: Boolean)
}
