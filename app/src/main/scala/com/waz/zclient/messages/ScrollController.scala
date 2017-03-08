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

import android.support.v7.widget.RecyclerView
import com.waz.model.ConvId
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.messages.MessagesListView.UnreadIndex
import com.waz.zclient.messages.ScrollController.Scroll

class ScrollController(adapter: MessagesListView.Adapter, listHeight: Signal[Int])(implicit ec: EventContext) {

  var targetPosition = Option.empty[Int]

  val onScrollToBottomRequested = EventStream[Int]

  var shouldScrollToBottom = false

  val scrollToPositionRequested = EventStream[Int]

  def onScrolled(lastVisiblePosition: Int) = shouldScrollToBottom = lastVisiblePosition == lastPosition

  def onDragging(): Unit = {
    shouldScrollToBottom = false
    targetPosition = None
  }

  private def lastPosition = adapter.getItemCount - 1

  private val onListLoaded = EventStream[UnreadIndex]

  private val onMessageAdded = EventStream[Int]

  private var prevCount = 0
  private var prevConv = ConvId()

  adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver {
    override def onChanged(): Unit = {
      if (prevConv != adapter.getConvId || prevCount == 0) {
        shouldScrollToBottom = adapter.getUnreadIndex.index == adapter.getItemCount && targetPosition.isEmpty
        targetPosition match {
          case Some(pos) =>
            scrollToPositionRequested ! pos
          case _ =>
            onListLoaded !adapter.getUnreadIndex
        }
      }

      prevConv = adapter.getConvId
      prevCount = adapter.getItemCount
    }

    override def onItemRangeInserted(positionStart: Int, itemCount: Int): Unit = {
      if (adapter.getItemCount == positionStart + itemCount)
        onMessageAdded ! adapter.getItemCount
    }
  })

  val onScroll = EventStream.union(
    onListLoaded map { case UnreadIndex(pos) => Scroll(pos, smooth = false) },
    onScrollToBottomRequested.map(_ => Scroll(lastPosition, smooth = true)),
    listHeight.onChanged.filter(_ => shouldScrollToBottom && targetPosition.isEmpty).map(_ => Scroll(lastPosition, smooth = false)),
    listHeight.onChanged.filter(_ => !shouldScrollToBottom && targetPosition.nonEmpty).map(_ => Scroll(targetPosition.get, smooth = false)),
    onMessageAdded.filter(_ => shouldScrollToBottom && targetPosition.isEmpty).map(_ => Scroll(lastPosition, smooth = true)),
    scrollToPositionRequested.map(pos => Scroll(pos, smooth = false))
  ) .filter(_.position >= 0)
}

object ScrollController {
  case class Scroll(position: Int, smooth: Boolean)
}
