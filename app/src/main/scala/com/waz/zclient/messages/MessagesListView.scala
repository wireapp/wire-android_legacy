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

import android.content.Context
import android.support.v7.widget.RecyclerView.OnScrollListener
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.ConvId
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.messages.ScrollController.Scroll

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  val adapter = new MessagesListAdapter
  val scrollController = new ScrollController(adapter)

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)

  scrollController.onScroll.on(Threading.Ui) { case Scroll(pos, smooth) =>
    verbose(s"Scrolling to pos: $pos")
    // TODO: implement smooth scrolling
    layoutManager.scrollToPositionWithOffset(pos, 0) //TODO may have to calculate different offset for images and large assets?
  }

  addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = newState match {
      case RecyclerView.SCROLL_STATE_IDLE =>
        scrollController.onScrolled(layoutManager.findLastCompletelyVisibleItemPosition())
      case RecyclerView.SCROLL_STATE_DRAGGING =>
        scrollController.onDragging()
      case _ =>
    }
  })

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    super.onLayout(changed, l, t, r, b)
    scrollController.listHeight ! (b - t)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! layoutManager.findLastCompletelyVisibleItemPosition()
}

object MessagesListView {

  trait Adapter {
    def initialLastReadIndex: Signal[(ConvId, Int)]
    def msgCount: Signal[Int]
    def getItemCount: Int
  }
}

case class MessageViewHolder(view: MessageView) extends RecyclerView.ViewHolder(view)

