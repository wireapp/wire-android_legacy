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
import android.support.v17.leanback.widget.VerticalGridView
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.util.AttributeSet
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog._
import com.waz.model.{MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector, ViewHelper, R}

class MessagesListView(context: Context, attrs: AttributeSet, style: Int) extends VerticalGridView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setHasFixedSize(true)
  setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false))
  setAdapter(new MessagesListAdapter(this))
}

object MessagesListView {
  private implicit val Tag: LogTag = logTagFor[MessagesListView]
}

case class MessageViewHolder(view: MessageView) extends RecyclerView.ViewHolder(view) {

}

class MessagesListAdapter(view: View)(implicit inj: Injector, ev: EventContext) extends RecyclerView.Adapter[MessageViewHolder]() with Injectable {
  import MessagesListAdapter._

  val zms = inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
  val selectedConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }

  val messages = new RecyclerDataSet[MessageId, MessageData](this) {
    override def getId(v: MessageData): MessageId = v.id
  }

  zms.zip(selectedConv) .flatMap { case (zs, conv) => zs.messagesStorage.getEntries(conv) } .on(Threading.Ui) { ms =>
    // TODO: don't use MessagesCursor, don't load all messages every time,
    // load only some window around current position
    verbose(s"loaded cursor: ${ms.size}")
    messages.set(IndexedSeq.tabulate(ms.size)(ms(_).message))
  }

  override def getItemCount: Int = messages.length

  override def getItemViewType(position: Int): Int = MessageView.viewType(messages(position).msgType)

  override def onBindViewHolder(holder: MessageViewHolder, position: Int): Unit =
    holder.view.set(messages(position), if (position == 0) None else Some(messages(position - 1)))

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    new MessageViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.message_view, parent, false).asInstanceOf[MessageView])
}

object MessagesListAdapter {
  private implicit val Tag: LogTag = logTagFor[MessagesListAdapter]

  implicit val MessageOrdering: Ordering[MessageData] = Ordering.by(_.time.toEpochMilli)
}
