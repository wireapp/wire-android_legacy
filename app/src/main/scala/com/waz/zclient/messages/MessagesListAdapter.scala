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

import android.view.ViewGroup
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, Dim2, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessagesListView.UnreadIndex
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.{Injectable, Injector}

class MessagesListAdapter(listDim: Signal[Dim2])(implicit inj: Injector, ec: EventContext)
  extends MessagesListView.Adapter() with Injectable { adapter =>

  verbose("MessagesListAdapter created")

  val zms = inject[Signal[ZMessaging]]
  val listController = inject[MessagesController]
  val selectedConversation = inject[SelectionController].selectedConv
  val ephemeralCount = Signal(Set.empty[MessageId])

  var unreadIndex = UnreadIndex(0)

  val conv = for {
    zs <- zms
    convId <- selectedConversation
    conv <- Signal future zs.convsStorage.get(convId)
  } yield conv

  val cursor = for {
    zs <- zms
    Some(c) <- conv
  } yield
    (new RecyclerCursor(c.id, zs, notifier), c.convType)

  private var messages = Option.empty[RecyclerCursor]
  private var convId = ConvId()
  private var convType = ConversationType.Group

  cursor.on(Threading.Ui) { case (c, tpe) =>
    if (!messages.contains(c)) {
      verbose(s"cursor changed: ${c.count}")
      messages.foreach(_.close())
      messages = Some(c)
      convType = tpe
      convId = c.conv
      notifier.notifyDataSetChanged()
    }
  }

  override def getConvId: ConvId = convId

  override def getUnreadIndex = unreadIndex

  override def getItemCount: Int = messages.fold(0)(_.count)

  def message(position: Int) = messages.get.apply(position)

  def lastReadIndex = messages.fold(-1)(_.lastReadIndex())

  override def getItemViewType(position: Int): Int = MessageView.viewType(message(position).message.msgType)

  override def onBindViewHolder(holder: MessageViewHolder, pos: Int): Unit = {
    onBindViewHolder(holder, pos, new util.ArrayList[AnyRef])
  }

  override def onBindViewHolder(holder: MessageViewHolder, pos: Int, payloads: util.List[AnyRef]): Unit = {
    verbose(s"onBindViewHolder: position: $pos")
    val data = message(pos)

    val isLast = pos == adapter.getItemCount - 1
    val isSelf = zms.currentValue.exists(_.selfUserId == data.message.userId)
    val isFirstUnread = pos > 0 && !isSelf && unreadIndex.index == pos
    val isLastSelf = listController.isLastSelf(data.message.id)
    val opts = MsgBindOptions(pos, isSelf, isLast, isLastSelf, isFirstUnread = isFirstUnread, listDim.currentValue.getOrElse(Dim2(0, 0)), convType)

    val prev = if (pos == 0) None else Some(message(pos - 1).message)
    val next = if (isLast) None else Some(message(pos + 1).message)
    holder.bind(data, prev, next, opts)
    if (data.message.isEphemeral) {
      ephemeralCount.mutate(_ + data.message.id)
    }
  }

  override def onViewRecycled(holder: MessageViewHolder): Unit = {
    if (holder.view.isEphemeral) {
      holder.id.foreach(id =>
        ephemeralCount.mutate(_ - id))
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
    MessageViewHolder(MessageView(parent, viewType), adapter)

  def positionForMessage(messageData: MessageData) =
    cursor.head.flatMap { _._1.positionForMessage(messageData) } (Threading.Background)

  lazy val notifier = new RecyclerNotifier {
    // view depends on two message entries,
    // most importantly, view needs to be refreshed if previous message was added or removed

    private def notifyChangedIfExists(position: Int) =
      if (position >= 0 && position < getItemCount)
        adapter.notifyItemChanged(position)

    override def notifyItemRangeInserted(index: Int, length: Int) = {
      adapter.notifyItemRangeInserted(index, length)
      notifyChangedIfExists(index + length + 1)
    }

    override def notifyItemRangeChanged(index: Int, length: Int) =
      adapter.notifyItemRangeChanged(index, length)

    override def notifyItemRangeRemoved(pos: Int, count: Int) = {
      adapter.notifyItemRangeRemoved(pos, count)
      notifyChangedIfExists(pos)
    }

    override def notifyDataSetChanged() = {
      unreadIndex = UnreadIndex(lastReadIndex + 1)
      adapter.notifyDataSetChanged()
    }
  }

}
