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

import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.conversation.ConversationController
import com.wire.signals.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient.{Injectable, Injector}
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessagesPagedListAdapter._
import com.waz.threading.Threading._

class MessagesPagedListAdapter()(implicit ec: EventContext, inj: Injector)
  extends PagedListAdapter[MessageAndLikes, MessageViewHolder](MessageDataDiffCallback)
    with Injectable
    with DerivedLogTag {

  private lazy val listController = inject[MessagesController]
  var convInfo: MessageAdapterData = MessageAdapterData.Empty
  var listDim: Dim2 = Dim2(0, 0)
  val onScrollRequested: SourceStream[(MessageData, Int)] = EventStream[(MessageData, Int)]()

  private var lastRead = RemoteInstant.Epoch
  inject[ConversationController].currentConv.map(_.lastRead).filter(_ != lastRead).onUi { time =>
    lastRead = time
    notifyDataSetChanged()
  }

  private val ephemerals = Signal[Set[MessageId]](Set.empty[MessageId])
  val hasEphemeral: Signal[Boolean] = ephemerals.map(_.nonEmpty)

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
        MessageViewHolder(MessageView(parent, viewType), this)

  override def onBindViewHolder(holder: MessageViewHolder, position: Int): Unit = {
    Option(getItem(position)).foreach { m =>

      val prev = if ((position + 1) < getItemCount) Option(getItem(position + 1)) else None
      val next = if (position == 0) None else Option(getItem(position - 1))

      val isSelf = m.message.userId == convInfo.selfId
      val isLast = position == 0
      val isLastSelf = listController.isLastSelf(m.message.id)
      val isFirstUnread = prev.exists(_.message.time == lastRead)

      val opts = MsgBindOptions(position, isSelf, isLast, isLastSelf, isFirstUnread = isFirstUnread,
        listDim, convInfo.isGroup, convInfo.teamId, convInfo.canHaveLink, Option(convInfo.selfId))

      holder.bind(m, prev.map(_.message), next.map(_.message), opts)
      ephemerals.mutate { set =>
        if (m.message.isEphemeral)
          set + m.message.id
        else
          set
      }
    }
  }

  override def getItemViewType(position: Int): Int =
    Option(getItem(position)).map(m => MessageView.viewType(m.message.msgType)).getOrElse(1)

  def unreadIsLast: Boolean = getItemCount > 0 && Option(getItem(0)).exists(_.message.time == lastRead)

  def positionForMessage(mId: MessageId): Option[Int] = for {
    list <- Option(getCurrentList)
    ds <- Option(list.getDataSource)
    pos <- ds.asInstanceOf[MessageDataSource].positionForMessage(mId)
  } yield pos

  override def onViewRecycled(holder: MessageViewHolder): Unit = {
    holder.message.currentValue.foreach { m =>
      ephemerals.mutate(_ - m.id)
    }
  }
}

object MessagesPagedListAdapter {
  val MessageDataDiffCallback: DiffUtil.ItemCallback[MessageAndLikes] = new DiffUtil.ItemCallback[MessageAndLikes] {
    override def areItemsTheSame(o: MessageAndLikes, n: MessageAndLikes): Boolean = n.message.id == o.message.id
    override def areContentsTheSame(o: MessageAndLikes, n: MessageAndLikes): Boolean =
      areMessageAndLikesTheSame(o, n)
  }

  def areMessageContentsTheSame(prev: MessageData, updated: MessageData): Boolean = {
    updated.contentString == prev.contentString &&
      updated.expired == prev.expired &&
      updated.imageDimensions == prev.imageDimensions &&
      updated.content.find(_.openGraph.nonEmpty) == prev.content.find(_.openGraph.nonEmpty) &&
      updated.content.find(_.richMedia.nonEmpty) == prev.content.find(_.richMedia.nonEmpty) &&
      updated.assetId == prev.assetId
  }

  def areMessageAndLikesTheSame(prev: MessageAndLikes, updated: MessageAndLikes): Boolean = {
    areMessageContentsTheSame(prev.message, updated.message) &&
    prev.likes.toSet == updated.likes.toSet &&
    prev.quote == updated.quote
  }
}
