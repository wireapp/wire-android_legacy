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
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}

class MessagesListAdapter()(implicit inj: Injector, ev: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable with MessagesListView.Adapter {
  adapter =>

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val selectedConversation = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }
  val cursor = zms.zip(selectedConversation) map { case (zs, conv) => new RecyclerCursor(conv, zs, adapter) }

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
