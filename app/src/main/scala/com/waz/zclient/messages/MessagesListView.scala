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
import android.view.ViewGroup
import com.waz.ZLog._
import com.waz.model.MessageData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.context.{ScrollAdapter, ScrollController}
import com.waz.zclient.{Injectable, Injector, ViewHelper}

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends RecyclerView(context, attrs, style) with ViewHelper {

  import MessagesListView._
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val scrollController = inject[ScrollController]

  val layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  val adapter = new MessagesListAdapter

  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)

  scrollController.adapter ! adapter

  scrollController.scrollPosition.on(Threading.Ui) { pos =>
    verbose(s"Scrolling to pos: $pos")
    layoutManager.scrollToPositionWithOffset(pos, 0) //TODO may have to calculate different offset for images and large assets?
  }

  scrollController.adapter ! adapter

  addOnScrollListener(new OnScrollListener {
    override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int): Unit = {
      scrollController.lastVisibleItemIndex ! layoutManager.findLastCompletelyVisibleItemPosition()
    }
  })

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    super.onLayout(changed, l, t, r, b)
    scrollController.listHeight ! (b - t)
  }
}

object MessagesListView {
  private implicit val Tag: LogTag = logTagFor[MessagesListView]
}

case class MessageViewHolder(view: MessageView) extends RecyclerView.ViewHolder(view)

class MessagesListAdapter()(implicit inj: Injector, ev: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable with ScrollAdapter {
  adapter =>
  import MessagesListAdapter._

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val selectedConversation = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }
  val cursor = zms.zip(selectedConversation) map { case (zs, conv) => new RecyclerCursor(conv, zs, adapter) }

  override val initialLastReadIndex = cursor.flatMap { c => c.initialLastReadIndex.map((c.conv, _)) }
  override val msgCount = cursor.flatMap(_.countSignal)

  private var messages = Option.empty[RecyclerCursor]

  cursor.on(Threading.Ui) { c =>
    messages.foreach(_.close())
    messages = Some(c)
    notifyDataSetChanged()
  }

  zms.zip(selectedConversation).on(Threading.Ui) { case (zs, conv) =>
    messages.foreach(_.close())
    messages = Some(new RecyclerCursor(conv, zs, adapter))
    notifyDataSetChanged()
  }

  override def getItemCount: Int = messages.fold(0)(_.count)

  private def message(position: Int) = messages.map(_.apply(position).message).orNull

  override def getItemViewType(position: Int): Int = MessageView.viewType(message(position).msgType)

  override def onBindViewHolder(holder: MessageViewHolder, position: Int): Unit = {
    zms.currentValue.foreach { zms =>
      selectedConversation.currentValue.foreach { convId =>
        val curLastRead = zms.messagesStorage.getEntries(convId).map(_.lastReadIndex).currentValue.getOrElse(-1)
        if (curLastRead > 0 && position > curLastRead) {
          verbose(s"Setting last read to $position")
          zms.convsUi.setLastRead(convId, message(position))
        }
      }
    }

    // FIXME - view depends on two message entries, so it should listen for changes of both of them,
    // most importantly, view needs to be refreshed if previous message was added or removed
    holder.view.set(position, message(position), if (position == 0) None else Some(message(position - 1)))
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    MessageViewHolder(MessageView(parent, viewType))
}

object MessagesListAdapter {
  private implicit val Tag: LogTag = logTagFor[MessagesListAdapter]

  implicit val MessageOrdering: Ordering[MessageData] = Ordering.by(_.time.toEpochMilli)
}
