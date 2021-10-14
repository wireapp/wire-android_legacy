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

import android.app.Activity
import androidx.paging.PagedList
import android.content.Context
import androidx.recyclerview.widget.RecyclerView.{OnScrollListener, ViewHolder}
import android.util.AttributeSet
import android.view.WindowManager
import androidx.recyclerview.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Dim2, MessageData}
import com.waz.service.assets.AssetStatus
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.wire.signals.{EventContext, Signal}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.{Injectable, Injector, ViewHelper}
import com.waz.threading.Threading._

class MessagesListView(context: Context, attrs: AttributeSet, style: Int)
  extends RecyclerView(context, attrs, style) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private val messagesController = inject[MessagesController]
  private val messageActionsController = inject[MessageActionsController]
  private val messagePagedListController = inject[MessagePagedListController]
  private val collectionsController = inject[CollectionController]

  val viewDim = Signal[Dim2]()
  val realViewHeight = Signal[Int]()
  val layoutManager = new MessagesListLayoutManager(context, LinearLayoutManager.VERTICAL, true)
  val adapter = new MessagesPagedListAdapter()
  val scrollController = new ScrollController(adapter, this, layoutManager)

  private val plCallback: PagedList.Callback = new PagedList.Callback {

    private def notifyChanged(): Unit = {
      scrollController.onPagedListChanged()
      adapter.notifyDataSetChanged()
    }

    override def onChanged(position: Int, count: Int): Unit = notifyChanged()

    override def onInserted(position: Int, count: Int): Unit = notifyChanged()

    override def onRemoved(position: Int, count: Int): Unit = notifyChanged()
  }

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)

  messagePagedListController.pagedListData.onUi { case (data, PagedListWrapper(pl), messageToReveal) =>
    pl.addWeakCallback(null, plCallback)
    adapter.convInfo = data
    adapter.submitList(pl)

    val dataSource = pl.getDataSource.asInstanceOf[MessageDataSource]
    messageToReveal
      .flatMap(mtr => dataSource.positionForMessage(mtr).filter(_ >= 0))
      .foreach { position =>
        scrollController.onPagedListReplaced(pl)
        scrollController.scrollToPositionRequested ! position
      }
  }

  scrollController.reachedQueuedScroll.filter(_.force).onUi { _ =>
    messageActionsController.messageToReveal ! None
  }

  viewDim.onUi { dim =>
    verbose(l"viewDim($dim)")
    adapter.listDim = dim
    adapter.notifyDataSetChanged()
  }

  realViewHeight.onChanged.pipeTo(scrollController.onListHeightChanged)

  adapter.onScrollRequested.onUi { case (message, _) =>
    collectionsController.focusedItem ! None // needed in case we requested a scroll to the same message again
    collectionsController.focusedItem ! Some(message)
  }

  setItemAnimator(new DefaultItemAnimator {
    // always reuse view holder, we will handle animations ourselves
    override def canReuseUpdatedViewHolder(viewHolder: ViewHolder, payloads: java.util.List[AnyRef]): Boolean = true
  })

  addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = newState match {
      case RecyclerView.SCROLL_STATE_IDLE =>
        val page = inject[INavigationController].getCurrentPage
        if (page == Page.MESSAGE_STREAM) {
          messagesController.scrolledToBottom ! (layoutManager.findLastCompletelyVisibleItemPosition() == 0)
        }

      case RecyclerView.SCROLL_STATE_DRAGGING => {
        messagesController.scrolledToBottom ! false
        Option(getContext).map(_.asInstanceOf[Activity]).foreach(a => KeyboardUtils.hideKeyboard(a))
      }
      case _ =>
    }
  })

  adapter.hasEphemeral.onUi { hasEphemeral =>
    Option(getContext).foreach {
      case a: Activity =>
        if (hasEphemeral)
          a.getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else
          a.getWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      case _ => // not attahced, ignore
    }
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    //We don't want the original height of the view to change if the keyboard comes up, or else images will be resized to
    //fit in the small space left. So only let the height change if for some reason the new height is bigger (shouldn't happen)
    //i.e., height in viewDim should always represent the height of the screen without the keyboard shown.
    viewDim.mutateOrDefault({ case Dim2(_, h) => Dim2(r - l, math.max(h, b - t)) }, Dim2(r - l, b - t))
    realViewHeight ! b - t
    super.onLayout(changed, l, t, r, b)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! true
}

object MessagesListView {

  val MaxSmoothScroll = 50

  case class UnreadIndex(index: Int) extends AnyVal
}

case class MessageViewHolder(view: MessageView, adapter: MessagesPagedListAdapter)(implicit ec: EventContext, inj: Injector)
  extends RecyclerView.ViewHolder(view)
    with Injectable
    with DerivedLogTag {

  private val selection = inject[ConversationController].messages
  private val msgsController = inject[MessagesController]
  private lazy val assets = inject[AssetsController]

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

  private val messageRead = for {
    Some(convId) <- msgsController.fullyVisibleMessagesList
    msg          <- message.filter(_.convId == convId)
    isSentAsset  =  msg.isAssetMessage && msg.state == Message.Status.SENT
    status       <- if (isSentAsset)
                      msg.assetId
                         .fold(Signal.const(Option.empty[AssetStatus]))(aId => assets.assetStatusSignal(aId).map { case (status, _) => Some(status) })
                    else
                      Signal.const(Option.empty[AssetStatus])
  } yield (isSentAsset, status) match {
    case (false, _)                     => Some(msg)
    case (true, Some(AssetStatus.Done)) => Some(msg)
    case _                              => None
  }

  messageRead.onUi {
    case Some(msg) => msgsController.onMessageRead(msg)
    case None      =>
  }

  def bind(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): Unit = {
    view.set(msg, prev, next, opts, adapter)
    message ! msg.message
    this.opts = Some(opts)
    _isFocused = selection.isFocused(msg.message.id)
  }
}
