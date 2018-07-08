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

import java.util

import android.view.ViewGroup
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessagesListView.UnreadIndex
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.{Injectable, Injector}

class MessagesListAdapter(listDim: Signal[Dim2])(implicit inj: Injector, ec: EventContext)
  extends MessagesListView.Adapter() with Injectable { adapter =>

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val listController = inject[MessagesController]
  lazy val conversationController = inject[ConversationController]
  val ephemeralCount = Signal(Set.empty[MessageId])

  var unreadIndex = UnreadIndex(0)

  val cursor = (for {
    zs      <- zms
    convId  <- conversationController.currentConvId
    isGroup <- Signal.future(zs.conversations.isGroupConversation(convId))
    conv    <- zs.convsStorage.signal(convId)
  } yield (zs, conv.id, isGroup, isGroup && conv.team.isDefined && conv.team == zs.teamId && !conv.isTeamOnly)).map {
    case (zs, convId, group, canHaveLink) => (new RecyclerCursor(convId, zs, notifier), zs.teamId, convId, group, canHaveLink)
  }

  private var _cursor     = Option.empty[RecyclerCursor]
  private var conv        = Option.empty[ConvId]
  private var canHaveLink = false
  private var teamId      = Option.empty[TeamId]
  private var isGroup     = false

  cursor.onUi { case (c, teamId, conv, group, canHaveLink) =>
    if (!_cursor.contains(c)) {
      verbose(s"cursor changed: ${c.count}")
      _cursor.foreach(_.close())
      _cursor = Some(c)
      this.conv = Some(conv)
      this.teamId = teamId
      this.isGroup = group
      this.canHaveLink = canHaveLink
      notifier.notifyDataSetChanged()
    }
  }

  override def getConvId = conv

  override def getUnreadIndex = unreadIndex

  override def getItemCount: Int = _cursor.fold(0)(_.count)

  def message(position: Int) = _cursor.get.apply(position)

  def lastReadIndex = _cursor.fold(-1)(_.lastReadIndex)

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
    val opts = MsgBindOptions(pos, isSelf, isLast, isLastSelf, isFirstUnread = isFirstUnread, listDim.currentValue.getOrElse(Dim2(0, 0)), isGroup, teamId, canHaveLink)

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
