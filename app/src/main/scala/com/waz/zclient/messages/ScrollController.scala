/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import com.waz.ZLog
import com.waz.model.ConvId
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.messages.MessagesListView.UnreadIndex
import com.waz.zclient.messages.ScrollController.{BottomScroll, LastVisiblePosition, PositionScroll, Scroll}
import com.waz.ZLog.ImplicitTag._

class ScrollController(adapter: MessagesListView.Adapter, listHeight: Signal[Int])(implicit ec: EventContext) {

  var targetPosition = Option.empty[Int]
  private var lastVisiblePosition = LastVisiblePosition(0, lastMessage = false)
  private var dragging = false
  private var prevCount = 0
  private var prevConv = Option.empty[ConvId]

  val scrollToPositionRequested = EventStream[Int]
  val onScrollToBottomRequested = EventStream[Boolean]
  private val onListLoaded = EventStream[UnreadIndex]
  private val onMessageAdded = EventStream[Int]

  def shouldScrollToBottom = targetPosition.isEmpty && !dragging && adapter.getUnreadIndex.index == adapter.getItemCount

  def onScrolled(lastVisiblePosition: Int) = {
    this.lastVisiblePosition = LastVisiblePosition(lastVisiblePosition, lastVisiblePosition == lastPosition)
    dragging = false
    ZLog.verbose(s"onScrolled $lastVisiblePosition")
  }

  def onScrolledInvisible() = this.lastVisiblePosition = LastVisiblePosition(lastVisiblePosition.position, lastVisiblePosition.position == lastPosition)

  def onDragging(): Unit = {
    dragging = true
    targetPosition = None
    ZLog.verbose(s"onDragging")
  }

  private def lastPosition = adapter.getItemCount - 1

  adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver {
    override def onChanged(): Unit = {
      ZLog.verbose(s"AdapterDataObserver onChanged prevCount: $prevCount, adapter item count: ${adapter.getItemCount}, prevConv: $prevConv, adapter conv: ${adapter.getConvId}")
      if (prevConv.isDefined && prevConv != adapter.getConvId || prevCount == 0) {
        targetPosition match {
          case Some(pos) =>
            scrollToPositionRequested ! pos
          case _ if shouldScrollToBottom && lastVisiblePosition.lastMessage =>
            onScrollToBottomRequested ! false
          case _ =>
            lastVisiblePosition = LastVisiblePosition(adapter.getUnreadIndex.index, adapter.getUnreadIndex.index == adapter.getItemCount - 1 || adapter.getItemCount == 0)
            onListLoaded ! adapter.getUnreadIndex
        }
      }

      prevConv = adapter.getConvId
      prevCount = adapter.getItemCount
    }

    override def onItemRangeInserted(positionStart: Int, itemCount: Int): Unit = {
      ZLog.verbose(s"AdapterDataObserver onItemRangeInserted positionStart : $positionStart, itemCount: $itemCount, prevCount: $prevCount, adapter item count: ${adapter.getItemCount}")
      if (itemCount == adapter.getItemCount)
        onChanged()
      else if (adapter.getItemCount == positionStart + itemCount && positionStart != 0) {
        onMessageAdded ! positionStart + itemCount - 1
        prevCount = adapter.getItemCount
      }
    }
  })

  val onScroll: EventStream[Scroll] = EventStream.union(
    onListLoaded.filter(_.index > 0).map { case UnreadIndex(pos) => PositionScroll(pos, smooth = false) },
    onScrollToBottomRequested.map(smooth => BottomScroll(smooth = smooth)),
    listHeight.onChanged.filter(_ => shouldScrollToBottom && targetPosition.isEmpty && lastVisiblePosition.lastMessage).map(_ => BottomScroll(smooth = false)),
    listHeight.onChanged.filter(_ => !shouldScrollToBottom && targetPosition.nonEmpty).map(_ => PositionScroll(targetPosition.get, smooth = false)),
    onMessageAdded.filter(_ => !dragging && targetPosition.isEmpty && lastVisiblePosition.lastMessage).map(_ => BottomScroll(smooth = true)),
    scrollToPositionRequested.map(pos => PositionScroll(pos, smooth = false))
  )
}

object ScrollController {
  trait Scroll
  case class PositionScroll(position: Int, smooth: Boolean) extends Scroll
  case class BottomScroll(smooth: Boolean) extends Scroll

  case class LastVisiblePosition(position: Int, lastMessage: Boolean)
}
