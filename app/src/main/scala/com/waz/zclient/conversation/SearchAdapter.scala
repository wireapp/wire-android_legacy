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
package com.waz.zclient.conversation

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.ViewGroup
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.{ContentSearchQuery, MessageFilter}
import com.waz.model.{ConvId, ConversationData, Dim2}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.RecyclerCursor
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.views.{SearchResultRowView, TextSearchResultRowView}
import com.waz.zclient.{Injectable, Injector}

/*
TODO: some of this stuff is duplicated from MessagesListAdapter. Maybe there's a possibility of some refactoring and create a 'base adapter' for messageCursors
 */
class SearchAdapter()(implicit context: Context, injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[ViewHolder] with Injectable { adapter =>

  val zms = inject[Signal[ZMessaging]]
  val selectedConversation = inject[SelectionController].selectedConv
  val contentSearchQuery = inject[CollectionController].contentSearchQuery

  val conv = for {
    zs <- zms
    convId <- selectedConversation
    conv <- Signal future zs.convsStorage.get(convId)
  } yield conv

  val cursor = for {
    zs <- zms
    Some(c) <- conv
    query <- contentSearchQuery
  } yield
    new RecyclerCursor(c.id, zs, notifier, messageFilter = Some(MessageFilter(None, Some(query))))

  private var messages = Option.empty[RecyclerCursor]
  private var convId = ConvId()

  val cursorLoader = for{
    c <- cursor
    true <- c.cursorLoaded
  } yield c

  cursorLoader.on(Threading.Ui) { c =>
    if (!messages.contains(c)) {
      verbose(s"cursor changed: ${c.count}")
      messages.foreach(_.close())
      messages = Some(c)
      convId = c.conv
      notifier.notifyDataSetChanged()
    }
  }

  setHasStableIds(true)

  def message(position: Int) = messages.get.apply(position)

  override def getItemCount: Int = {
    messages.fold(0)(_.count)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = {
    holder.asInstanceOf[SearchResultRowViewHolder].set(message(position))
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = {
    returning(new SearchResultRowViewHolder(new TextSearchResultRowView(context))) {
      _.setSearchQuerySignal(contentSearchQuery)
    }
  }

  override def getItemId(position: Int): Long = message(position).message.id.str.hashCode

  lazy val notifier = new RecyclerNotifier {

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
      adapter.notifyDataSetChanged()
    }
  }
}

class SearchResultRowViewHolder(view: SearchResultRowView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view){

  def setSearchQuerySignal(contentSearchQuery: Signal[ContentSearchQuery]): Unit = {
    contentSearchQuery{view.searchedQuery ! _}
  }

  def set(message: MessageAndLikes): Unit ={
    view.set(message, None, SearchResultView.DefaultBindingOptions)
  }
}

object SearchResultView{
  val DefaultBindingOptions = MsgBindOptions(0, isSelf = false, isLast = false, isLastSelf = false, isFirstUnread = false, listDimensions = Dim2.Empty, ConversationData.ConversationType.Unknown)
}
