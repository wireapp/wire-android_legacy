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
import android.view.ViewGroup
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.{Injectable, Injector}

class MessagesListAdapter()(implicit inj: Injector, ec: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable with MessagesListView.Adapter { adapter =>

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val selectedConversation = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }
  val cursor = zms.zip(selectedConversation) map { case (zs, conv) => new RecyclerCursor(conv, zs, adapter) }

  val onBindView = EventStream[Int]()

  override val initialLastReadIndex = cursor.flatMap { c => c.initialLastReadIndex.map((c.conv, _)) }
  override val msgCount = cursor.flatMap(_.countSignal)

  private var messages = Option.empty[RecyclerCursor]

  cursor.on(Threading.Ui) { c =>
    messages.foreach(_.close())
    verbose(s"cursor changed")
    messages = Some(c)
    notifyDataSetChanged()
  }

  override def getItemCount: Int = messages.fold(0)(_.count)

  def message(position: Int) = messages.map(_.apply(position).message).orNull

  override def getItemViewType(position: Int): Int = MessageView.viewType(message(position).msgType)

  override def onBindViewHolder(holder: MessageViewHolder, position: Int): Unit = {
    holder.bind(position, message(position), if (position == 0) None else Some(message(position - 1)))
    onBindView ! position
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    MessageViewHolder(MessageView(parent, viewType))


  // view depends on two message entries,
  // most importantly, view needs to be refreshed if previous message was added or removed
  // TODO: test if that actually works
  registerAdapterDataObserver(new RecyclerView.AdapterDataObserver {

    private def notifyChangedIfExists(position: Int) =
      if (position >= 0 && position < getItemCount) notifyItemChanged(position)

    override def onItemRangeRemoved(positionStart: Int, itemCount: Int): Unit =
      notifyChangedIfExists(positionStart)

    override def onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int): Unit =
      if (fromPosition < toPosition) {
        notifyChangedIfExists(fromPosition)
        notifyChangedIfExists(toPosition + itemCount)
      } else {
        notifyChangedIfExists(fromPosition + 1)
        notifyChangedIfExists(toPosition + itemCount + 1)
      }

    override def onItemRangeInserted(positionStart: Int, itemCount: Int): Unit =
      notifyChangedIfExists(positionStart + itemCount + 1)
  })
}
