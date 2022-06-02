/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import androidx.paging.PagedList
import androidx.recyclerview.widget.RecyclerView
import com.waz.service.messages.MessageAndLikes
import com.wire.signals.{EventContext, EventStream, SourceStream}
import com.waz.zclient.log.LogUI._
import ScrollController._
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogShow.SafeToLog
import com.waz.threading.Threading._

class ScrollController(adapter: MessagesPagedListAdapter, view: RecyclerView, layoutManager: MessagesListLayoutManager)
                      (implicit ec: EventContext)
  extends DerivedLogTag {

  private var lastVisiblePosition = 0
  private var dragging = false
  private var previousCount = Option.empty[Int]

  private var queuedScroll = Option.empty[Scroll]

  val onListLoaded: SourceStream[Int] = EventStream[Int]
  val scrollToPositionRequested: SourceStream[Int] = EventStream[Int]
  val onScrollToBottomRequested: SourceStream[Boolean] = EventStream[Boolean]
  val onMessageAdded: SourceStream[Int] = EventStream[Int]
  val onListHeightChanged: SourceStream[Int] = EventStream[Int]

  val reachedQueuedScroll: SourceStream[Scroll] = EventStream[Scroll]

  EventStream.zip(
    onListLoaded.map { pos => Scroll(pos, smooth = false, force = true) },
    onScrollToBottomRequested.filter(_ => queuedScroll.isEmpty).map { _ => BottomScroll(smooth = false) },
    onListHeightChanged.filter(_ => shouldScrollToBottom && lastVisiblePosition == LastMessageIndex).map { _ => BottomScroll(smooth = false) },
    onListHeightChanged.filter(_ => !shouldScrollToBottom && queuedScroll.nonEmpty).map { _ => queuedScroll.get },
    onMessageAdded.filter(_ => !dragging && queuedScroll.isEmpty && lastVisiblePosition == LastMessageIndex).map { _ => BottomScroll(smooth = false) },
    scrollToPositionRequested.map { pos => Scroll(pos, smooth = false, force = true) }
  ).onUi(processScroll)

  adapter.onScrollRequested.map(_._2).pipeTo(scrollToPositionRequested)

  view.addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit =
      newState match {
        case RecyclerView.SCROLL_STATE_DRAGGING => startDragging()
        case _ => stopDragging()
      }

    override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int): Unit = {

      def from: Int = {
        val f = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (f == -1) layoutManager.findFirstVisibleItemPosition() else f
      }

      def to: Int = {
        val t = layoutManager.findLastCompletelyVisibleItemPosition()
        if (t == -1) layoutManager.findLastVisibleItemPosition() else t
      }

      onScroll(from, to)
    }

  })

  def onPagedListChanged(): Unit = {
    queuedScroll.foreach(processScroll)
  }

  def onPagedListReplaced(pl: PagedList[MessageAndLikes]): Unit = {
    val newCount = pl.getDataSource.asInstanceOf[MessageDataSource].totalCount
    if (previousCount.exists(_ < newCount)) {
      onMessageAdded ! newCount - previousCount.getOrElse(0)
    } else {
      queuedScroll.foreach(processScroll)
    }
    previousCount = Some(newCount)
  }

  private def canScrollTo(pos: Int): Boolean =
    Option(adapter.getCurrentList).exists { list =>
      pos >= 0 && pos < list.size() && list.get(pos) != null
    }

  private def requestLoadAround(pos: Int): Unit =
    Option(adapter.getCurrentList).filter(list => pos >= 0 && pos < list.size())
      .foreach(_.loadAround(pos))

  private def processScroll(s: Scroll): Unit = {
    if (queuedScroll.forall(!_.force)) {
      queuedScroll = Some(s)
    }
    queuedScroll.foreach { scroll =>
      val target = scroll.pos

      if (scroll.pos == LastMessageIndex)
        layoutManager.snapToEnd()
      else
        layoutManager.snapToStart()

      if (scroll.smooth) {
        view.smoothScrollToPosition(target)
      } else {
        if (canScrollTo(target)) {
          layoutManager.snapToEnd()
          view.scrollToPosition(target)
        } else {
          requestLoadAround(target)
        }
      }
    }
  }

  private def onScroll(lastVisiblePosition: => Int, firstVisiblePosition: => Int): Unit = {
    this.lastVisiblePosition = lastVisiblePosition
    queuedScroll match {
      case Some(scroll @ Scroll(pos, _, _)) if pos >= lastVisiblePosition && pos <= firstVisiblePosition =>
        reachedQueuedScroll ! scroll
        queuedScroll = None
      case Some(scroll) =>
        processScroll(scroll)
      case _ =>
    }
  }

  private def startDragging(): Unit = {
    dragging = true
    queuedScroll = None
  }

  private def stopDragging(): Unit = {
    dragging = false
  }

  private def shouldScrollToBottom: Boolean = queuedScroll.isEmpty && !dragging && adapter.unreadIsLast
}

object ScrollController {
  case class Scroll(pos: Int, smooth: Boolean, force: Boolean = false) extends SafeToLog {
    override def toString: String = s"Scroll(pos: $pos, smooth: $smooth, force: $force)"
  }

  def BottomScroll(smooth: Boolean, force: Boolean = false) = Scroll(LastMessageIndex, smooth, force)
  val LastMessageIndex: Int = 0
}
