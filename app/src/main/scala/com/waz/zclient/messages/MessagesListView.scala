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
import android.support.v7.widget.RecyclerView.{ViewHolder, OnScrollListener}
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.{ConvId, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.ScrollController.Scroll
import com.waz.zclient.{Injectable, Injector, ViewHelper}

import scala.concurrent.duration._

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import MessagesListView._

  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  val adapter = new MessagesListAdapter
  val scrollController = new ScrollController(adapter)
  val lastRead = new LastReadUpdater(adapter, layoutManager)

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)

  scrollController.onScroll.on(Threading.Ui) { case Scroll(pos, smooth) =>
    verbose(s"Scrolling to pos: $pos")
    if (smooth) {
      val current = layoutManager.findFirstVisibleItemPosition()

      // jump closer to target position before scrolling, don't want to smooth scroll through many messages
      if (math.abs(current - pos) > MaxSmoothScroll)
        scrollToPosition(if (pos > current) pos - MaxSmoothScroll else pos + MaxSmoothScroll)

      smoothScrollToPosition(pos)
    } else {
      scrollToPosition(pos)
    }
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
    scrollController.listHeight ! (b - t)
    super.onLayout(changed, l, t, r, b)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! layoutManager.findLastCompletelyVisibleItemPosition()
}

object MessagesListView {

  val MaxSmoothScroll = 50

  trait Adapter {
    def initialLastReadIndex: Signal[(ConvId, Int)]
    def msgCount: Signal[Int]
    def getItemCount: Int
  }
}

case class MessageViewHolder(view: MessageView, adapter: MessagesListAdapter)(implicit ec: EventContext, inj: Injector) extends RecyclerView.ViewHolder(view) with Injectable {

  private val selection = inject[SelectionController].messages
  private var id: MessageId = _
  private var focused = false

  selection.focused.onChanged { f =>
    if (focused != f.contains(id)) adapter.notifyItemChanged(getAdapterPosition)
  }

  def bind(position: Int, msg: MessageAndLikes, prev: Option[MessageData]): Unit = {
    id = msg.message.id
    focused = selection.focused.currentValue.exists(_.contains(id))
    view.set(position, msg, prev, focused)
  }
}


class LastReadUpdater(adapter: MessagesListAdapter, layoutManager: LinearLayoutManager)(implicit injector: Injector, ev: EventContext) extends Injectable {

  val zmessaging = inject[Signal[ZMessaging]]

  private val lastBoundMessage = for {
    zms <- zmessaging
    index <- Signal.wrap(adapter.onBindView).throttle(500.millis)
  } yield (zms, index)

  lastBoundMessage.on(Threading.Ui) { case (zms, _) =>
    val index = layoutManager.findLastCompletelyVisibleItemPosition()
    if (index >= 0) {
      val msg = adapter.message(index).message
      zms.convsUi.setLastRead(msg.convId, msg)
    }
  }
}
