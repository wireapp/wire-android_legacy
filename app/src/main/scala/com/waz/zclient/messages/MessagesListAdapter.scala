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
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.{Injectable, Injector}

class MessagesListAdapter()(implicit inj: Injector, ec: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable with MessagesListView.Adapter { adapter =>

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val selectedConversation = inject[SelectionController].selectedConv
  val cursor = zms.zip(selectedConversation) map { case (zs, conv) => new RecyclerCursor(conv, zs, adapter) }

  val onBindView = EventStream[Int]()

  val messageStreamOpen = inject[NavigationController].messageStreamOpen

  override val initialLastReadIndex = cursor.flatMap { c => c.initialLastReadIndex.map((c.conv, _)) }
  override val msgCount = cursor.flatMap(_.countSignal)

  private var messages = Option.empty[RecyclerCursor]

  cursor.on(Threading.Ui) { c =>
    messages.foreach(_.close())
    verbose(s"cursor changed: ${c.count}")
    messages = Some(c)
    notifyDataSetChanged()
  }

  override def getItemCount: Int = messages.fold(0)(_.count)

  def message(position: Int) = messages.get.apply(position)

  override def getItemViewType(position: Int): Int = MessageView.viewType(message(position).message.msgType)

  override def onBindViewHolder(holder: MessageViewHolder, pos: Int): Unit = {
    verbose(s"onBindViewHolder: position: $pos")
    holder.bind(pos, message(pos), if (pos == 0) None else Some(message(pos - 1).message), isFirstUnread(pos))
    onBindView ! pos
  }

  def currentLastReadIndex() = messages.fold(-1) (_.lastReadIndex())

  private def isFirstUnread(pos: Int) =
    if (pos == 0) false
    else if (!zms.map(_.selfUserId).currentValue.contains(message(pos).message.userId))
      initialLastReadIndex.map { case (_, i) => i }.currentValue.getOrElse(-1) == pos - 1
    else false

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    MessageViewHolder(MessageView(parent, viewType), adapter)


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
