/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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

import java.util

import android.app.Activity
import android.content.Context
import android.support.v7.widget.RecyclerView.{OnScrollListener, ViewHolder}
import android.support.v7.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.WindowManager
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.{ConvId, Dim2, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.ScrollController.Scroll
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.{Injectable, Injector, ViewHelper}

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import MessagesListView._

  val viewDim = Signal[Dim2]()
  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
    setStackFromEnd(true)
    override def supportsPredictiveItemAnimations(): Boolean = true
  }
  val adapter = new MessagesListAdapter(viewDim)
  val scrollController = new ScrollController(adapter, viewDim.map(_.height))

  val messageActionsController = inject[MessageActionsController]

  viewDim.on(Threading.Ui){_ => adapter.notifyDataSetChanged()}

  messageActionsController.messageToReveal.flatMap{
    case Some(messageData) => adapter.positionForMessage(messageData)
    case _ => Signal.empty[Option[Int]]
  }.on(Threading.Background){ pos =>
    pos.foreach{ p =>
      scrollController.targetPosition = Some(p)
      scrollController.scrollToPositionRequested ! p
      messageActionsController.messageToReveal ! None
    }
  }

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)
  setItemAnimator(new DefaultItemAnimator {
    // always reuse view holder, we will handle animations ourselves
    override def canReuseUpdatedViewHolder(viewHolder: ViewHolder, payloads: util.List[AnyRef]): Boolean = true
  })

  adapter.ephemeralCount { set =>
    val count = set.size
    Option(getContext).foreach {
      case a:Activity => {
        count match {
          case 0 => a.getWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          case _ => a.getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
      }
      case _ => // not attahced, ignore
    }
  }

  scrollController.onScroll.on(Threading.Ui) { case Scroll(pos, smooth) =>
    verbose(s"Scrolling to pos: $pos, smooth: $smooth")
    val scrollTo = math.min(adapter.getItemCount - 1, pos)
    val alreadyScrolledToCorrectPosition = layoutManager.findLastCompletelyVisibleItemPosition() == pos
    if (alreadyScrolledToCorrectPosition) {
      scrollController.shouldScrollToBottom = true
    } else {
      stopScroll()
      if (smooth) {
        val current = layoutManager.findFirstVisibleItemPosition()
        // jump closer to target position before scrolling, don't want to smooth scroll through many messages
        if (math.abs(current - pos) > MaxSmoothScroll)
          layoutManager.scrollToPosition(if (pos > current) pos - MaxSmoothScroll else pos + MaxSmoothScroll)

        smoothScrollToPosition(pos) //TODO figure out how to provide an offset, we should scroll to top of the message
      } else {
        layoutManager.scrollToPosition(scrollTo)
      }
    }
  }

  addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = newState match {
      case RecyclerView.SCROLL_STATE_IDLE =>
        scrollController.onScrolled(layoutManager.findLastVisibleItemPosition())
      case RecyclerView.SCROLL_STATE_DRAGGING => {
        scrollController.onDragging()
        Option(getContext).map(_.asInstanceOf[Activity]).foreach(a => KeyboardUtils.hideKeyboard(a))
      }
      case _ =>
    }
  })

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    //We don't want the original height of the view to change if the keyboard comes up, or else images will be resized to
    //fit in the small space left. So only let the height change if for some reason the new height is bigger (shouldn't happen)
    //i.e., height in viewDim should always represent the height of the screen without the keyboard shown.
    viewDim.mutateOrDefault({ case Dim2(_, h) => Dim2(r - l, math.max(h, b - t)) }, Dim2(r - l, b - t))
    super.onLayout(changed, l, t, r, b)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! layoutManager.findLastCompletelyVisibleItemPosition()
}

object MessagesListView {

  val MaxSmoothScroll = 50

  case class UnreadIndex(index: Int) extends AnyVal

  abstract class Adapter extends RecyclerView.Adapter[MessageViewHolder] {
    def getConvId: ConvId
    def getUnreadIndex: UnreadIndex
  }
}

case class MessageViewHolder(view: MessageView, adapter: MessagesListAdapter)(implicit ec: EventContext, inj: Injector) extends RecyclerView.ViewHolder(view) with Injectable {

  private val selection = inject[SelectionController].messages
  private val msgsController = inject[MessagesController]

  val message = Signal[MessageData]
  def id = message.currentValue.map(_.id)

  private var opts = Option.empty[MsgBindOptions]
  private var _isFocused = false

  selection.focused.onChanged.on(Threading.Ui) { mId =>
    if (_isFocused != (id == mId)) adapter.notifyItemChanged(getAdapterPosition)
  }

  msgsController.lastSelfMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLastSelf != id.contains(m.id)) adapter.notifyItemChanged(getAdapterPosition)
    }
  }

  msgsController.lastMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLast != id.contains(m.id)) adapter.notifyItemChanged(getAdapterPosition)
    }
  }

  // mark message as read if message is bound while list is visible
  private val visibleMessage =
    msgsController.fullyVisibleMessagesList flatMap {
      case Some(convId) => message.filter(_.convId == convId)
      case None => Signal.empty[MessageData]
    }

  visibleMessage { msgsController.onMessageRead }

  def bind(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): Unit = {
    view.set(msg, prev, next, opts)
    message ! msg.message
    this.opts = Some(opts)
    _isFocused = selection.isFocused(msg.message.id)
  }
}
