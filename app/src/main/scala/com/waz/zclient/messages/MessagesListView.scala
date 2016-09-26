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
import com.waz.model.{ConvId, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.ItemAnimator.{FocusChanged, LikesChanged}
import com.waz.zclient.messages.ScrollController.Scroll
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.utils.RichView
import com.waz.zclient.{Injectable, Injector, ViewHelper}

import scala.concurrent.duration._

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  val adapter = new MessagesListAdapter
  val scrollController = new ScrollController(adapter)
  val lastRead = new LastReadController(adapter, layoutManager)

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)
  setItemAnimator(new ItemAnimator)

  scrollController.onScroll.on(Threading.Ui) { case Scroll(pos, smooth) =>
    verbose(s"Scrolling to pos: $pos")
      val scrollTo = math.min(adapter.getItemCount - 1, pos)
//    val itemHeight = findViewHolderForAdapterPosition(pos).itemView.getHeight
//    if (smooth) {
//      val current = layoutManager.findFirstVisibleItemPosition()
//
//      // jump closer to target position before scrolling, don't want to smooth scroll through many messages
//      if (math.abs(current - pos) > MaxSmoothScroll)
//        layoutManager.scrollToPositionWithOffset(if (pos > current) pos - MaxSmoothScroll else pos + MaxSmoothScroll, 0)
//
//      smoothScrollToPosition(pos) //TODO figure out how to provide an offset
//    } else {
      layoutManager.scrollToPositionWithOffset(scrollTo, 0)
//      scrollToPosition(pos)
//    }
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
    def selectedConversation: Signal[ConvId]

    def nextUnreadIndex: Signal[Int]
    def msgCount: Signal[Int]
    def getItemCount: Int
  }
}

case class MessageViewHolder(view: MessageView, adapter: MessagesListAdapter)(implicit ec: EventContext, inj: Injector) extends RecyclerView.ViewHolder(view) with Injectable {

  private val selection = inject[SelectionController].messages
  private var id: MessageId = _
  private var focused = false

  selection.focused.onChanged { f =>
    if (focused != f.contains(id)) adapter.notifyItemChanged(getAdapterPosition, FocusChanged)
  }

  def isFocused = focused

  def bind(position: Int, msg: MessageAndLikes, prev: Option[MessageData], isFirstUnread: Boolean, payloads: List[AnyRef]): Unit = {
    id = msg.message.id
    focused = selection.focused.currentValue.exists(_.contains(id))

    payloads.headOption.fold { //full update
      view.set(position, msg, prev, isFirstUnread)
    } { // partial update
      case FocusChanged => //nothing special to do
      case LikesChanged => view.getFooter.foreach(_.updateLikes(msg.likedBySelf, msg.likes))
      case _ => // not defined
    }
    view.getFooter.foreach(_.setVisible(focused || msg.likes.nonEmpty))

  }

}


class LastReadController(adapter: MessagesListAdapter, layoutManager: LinearLayoutManager)(implicit injector: Injector, ev: EventContext) extends Injectable {

  val zmessaging = inject[Signal[ZMessaging]]
  val messageStreamOpen = inject[NavigationController].messageStreamOpen

  private var hideUnread = CancellableFuture.successful[Unit](())

  //last read should only be updated each time we enter a conversation, whether the conv changed or not
  messageStreamOpen.onChanged.filter(_ == true) { _ =>
    val lastRead = adapter.currentLastReadIndex()
    verbose(s"setting last read index: $lastRead")
    adapter.showUnreadDot ! (lastRead + 1 < adapter.getItemCount)
    adapter.nextUnreadIndex ! lastRead + 1
    hideUnread.cancel()
    hideUnread = CancellableFuture.delay(3.seconds).map(_ => adapter.showUnreadDot ! false)(Threading.Background)
  }

  private val lastBoundMessage = for {
    zms <- zmessaging
    index <- Signal.wrap(adapter.onBindView).throttle(500.millis)
  } yield (zms, index)

  lastBoundMessage.zip(messageStreamOpen).on(Threading.Ui) {
    case ((zms, _), true) =>
      val index = layoutManager.findLastCompletelyVisibleItemPosition()
      if (index >= 0) {
        val msg = adapter.message(index).message
        verbose(s"Setting last read to pos:$index, $msg")
        zms.convsUi.setLastRead(msg.convId, msg)
      }
    case _ => //message stream not open

  }
}
