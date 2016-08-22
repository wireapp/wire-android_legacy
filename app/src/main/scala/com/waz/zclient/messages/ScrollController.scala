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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.events.{EventContext, EventStream, Signal}

class ScrollController(adapter: MessagesListView.Adapter)(implicit ec: EventContext) {

  val listHeight = Signal[Int]()

  val shouldScrollToBottom = Signal(false)

  val onScroll = EventStream[Int]()

  def onScrolled(scrolledToBottom: Boolean) = {

    if (adapter.getItemCount > 0) shouldScrollToBottom ! scrolledToBottom
  }

  adapter.initialLastReadIndex.onChanged {
    case (conv, lastReadIndex) =>
      verbose(s"initialLastReadIndex($conv, $lastReadIndex)")
      onScroll ! lastReadIndex
  }

  listHeight.onChanged { _ =>
    verbose(s"height changed")
    if (shouldScrollToBottom.currentValue.contains(true)) onScroll ! (adapter.getItemCount - 1)
  }

  adapter.msgCount.onChanged { _ =>
    verbose(s"msgs count changed")
    if (shouldScrollToBottom.currentValue.contains(true)) onScroll ! (adapter.getItemCount - 1)
  }
}
