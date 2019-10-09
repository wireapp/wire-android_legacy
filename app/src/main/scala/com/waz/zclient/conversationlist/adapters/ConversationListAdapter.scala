/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.conversationlist.adapters

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.{View, ViewGroup}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData, Uid}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter.{ConversationRowViewHolder, _}
import com.waz.zclient.conversationlist.views.{ConversationFolderListRow, ConversationListRow, IncomingConversationListRow, NormalConversationListRow}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.{R, ViewHelper}

abstract class ConversationListAdapter
  extends RecyclerView.Adapter[ConversationRowViewHolder]
    with RowClickListener
    with DerivedLogTag {

  setHasStableIds(true)

  val onConversationClick: SourceStream[ConvId] = EventStream[ConvId]()
  val onConversationLongClick: SourceStream[ConversationData] = EventStream[ConversationData]()

  protected var items: List[Item] = List.empty
  protected var maxAlpha = 1.0f

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
    notifyDataSetChanged()
  }

  /**
    * Replaces the data source and updates the views of the list.
    *
    * @param newItems the new data source.
    */
  protected def updateList(newItems: List[Item]): Unit = {
    DiffUtil.calculateDiff(new DiffCallback(items, newItems), false).dispatchUpdatesTo(this)
    items = newItems
  }

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position) match {
    case _: Item.IncomingRequests => IncomingViewType
    case _: Item.Header           => FolderViewType
    case _: Item.Conversation     => NormalViewType
  }

  override def getItemId(position: Int): Long = items(position) match {
    case Item.IncomingRequests(first, _) => first.str.hashCode
    case Item.Header(id, _, _)           => id.str.hashCode
    case Item.Conversation(data)         => data.id.str.hashCode
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = viewType match {
    case IncomingViewType => ViewHolderFactory.newIncomingConversationRowViewHolder(this, parent)
    case FolderViewType   => ViewHolderFactory.newConversationFolderRowViewHolder(this, parent)
    case NormalViewType   => ViewHolderFactory.newNormalConversationRowViewHolder(this, parent)
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = {
    (items(position), holder) match {
      case (incomingRequests: Item.IncomingRequests, viewHolder: IncomingConversationRowViewHolder) =>
        viewHolder.bind(incomingRequests.first, incomingRequests.numberOfRequests)
      case (header: Item.Header, viewHolder: ConversationFolderRowViewHolder) =>
        viewHolder.bind(header, isFirst = position == 0)
      case (conversation: Item.Conversation, viewHolder: NormalConversationRowViewHolder) =>
        viewHolder.bind(conversation.data)
      case _ =>
        error(l"Invalid view holder/data pair")
    }
  }

  override def onClick(position: Int): Unit = items(position) match {
    case Item.IncomingRequests(first, _) => onConversationClick ! first
    case Item.Conversation(data)         => onConversationClick ! data.id
    case _                               =>
  }

  override def onLongClick(position: Int): Boolean = items(position) match {
    case Item.Conversation(data) =>
      onConversationLongClick ! data
      true
    case _ =>
      false
  }
}

object ConversationListAdapter {

  val NormalViewType = 0
  val IncomingViewType = 1
  val FolderViewType = 2

  trait RowClickListener {
    def onClick(position: Int): Unit
    def onLongClick(position: Int): Boolean
  }

  sealed trait Item

  object Item {
    case class Header(id: Uid, title: String, isExpanded: Boolean) extends Item
    case class Conversation(data: ConversationData) extends Item
    case class IncomingRequests(first: ConvId, numberOfRequests: Int) extends Item
  }

  abstract class ConversationRowViewHolder(row: ConversationListRow, listener: RowClickListener)
    extends RecyclerView.ViewHolder(row)
      with View.OnClickListener
      with View.OnLongClickListener {

    itemView.setOnClickListener(this)
    itemView.setOnLongClickListener(this)

    override def onClick(v: View): Unit = listener.onClick(getAdapterPosition)
    override def onLongClick(v: View): Boolean = listener.onLongClick(getAdapterPosition)
  }

  class NormalConversationRowViewHolder(row: NormalConversationListRow, listener: RowClickListener)
    extends ConversationRowViewHolder(row, listener) {

    def bind(conversation: ConversationData): Unit = row.setConversation(conversation)
  }

  class IncomingConversationRowViewHolder(row: IncomingConversationListRow, listener: RowClickListener)
    extends ConversationRowViewHolder(row, listener) {

    def bind(first: ConvId, numberOfRequest: Int): Unit = row.setIncoming(first, numberOfRequest)
  }

  class ConversationFolderRowViewHolder(row: ConversationFolderListRow, listener: RowClickListener)
    extends ConversationRowViewHolder(row, listener) {

    def bind(header: Item.Header, isFirst: Boolean): Unit = {
      row.setTitle(header.title)
      row.setIsFirstHeader(isFirst)
      row.setIsExpanded(header.isExpanded)
    }
  }

  object ViewHolderFactory extends DerivedLogTag {
    import ViewHelper.inflate

    def newNormalConversationRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): NormalConversationRowViewHolder = {
      val row = returning(inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)) { r =>
        r.setAlpha(1f)
        r.setMaxAlpha(adapter.maxAlpha)

        // TODO: can we move this to our listener?
        r.setConversationCallback(new ConversationCallback {
          override def onConversationListRowSwiped(convId: String, view: View): Unit =
            r.conversationData.foreach(adapter.onConversationLongClick ! _)
        })
      }

      new NormalConversationRowViewHolder(row, listener = adapter)
    }

    def newIncomingConversationRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): IncomingConversationRowViewHolder = {
      val row = inflate[IncomingConversationListRow](R.layout.incoming_conv_list_item, parent, addToParent = false)
      new IncomingConversationRowViewHolder(row, listener = adapter)
    }

    def newConversationFolderRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): ConversationFolderRowViewHolder = {
      val row = inflate[ConversationFolderListRow](R.layout.conv_folder_list_item, parent, addToParent = false)
      new ConversationFolderRowViewHolder(row, listener = adapter)
    }
  }

  /**
    * A `DiffUtil.Callback` used with `DiffUtil` to efficiently update a `ConversationListAdapter`.
    *
    * @param oldList the current out of date list of items.
    * @param newList the new updated list of items.
    */
  class DiffCallback(oldList: Seq[Item], newList: Seq[Item]) extends DiffUtil.Callback {
    import Item._

    override def getOldListSize: Int = oldList.size
    override def getNewListSize: Int = newList.size

    override def areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = {
      (oldList(oldItemPosition), newList(newItemPosition)) match {
        case (_: Header,           _: Header)           => true
        case (_: Conversation,     _: Conversation)     => true
        case (_: IncomingRequests, _: IncomingRequests) => true
        case _                                          => false
      }
    }

    override def areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
      (oldList(oldItemPosition), newList(newItemPosition)) match {
        case (Header(_, title, isExpanded), Header(_, newTitle, newIsExpanded)) =>
          title == newTitle && isExpanded && newIsExpanded
        case (Conversation(data), Conversation(newData)) =>
          data == newData
        case (IncomingRequests(_, requests), IncomingRequests(_, newRequests)) =>
          requests == newRequests
        case _ =>
          false
      }
  }
}
