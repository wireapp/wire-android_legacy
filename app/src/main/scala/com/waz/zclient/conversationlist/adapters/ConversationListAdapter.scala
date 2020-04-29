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

import android.content.Context
import android.view.{View, ViewGroup}
import androidx.recyclerview.widget.{DiffUtil, RecyclerView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData, FolderId}
import com.waz.utils.events.{EventContext, EventStream, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter.{ConversationRowViewHolder, _}
import com.waz.zclient.conversationlist.views.{ConversationFolderListRow, ConversationListRow, IncomingConversationListRow, NormalConversationListRow}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

import scala.collection.mutable.ListBuffer

abstract class ConversationListAdapter (implicit context: Context, eventContext: EventContext, injector: Injector)
  extends RecyclerView.Adapter[ConversationRowViewHolder]
    with RowClickListener
    with DerivedLogTag
    with Injectable {

  setHasStableIds(true)

  val onConversationClick: SourceStream[ConvId] = EventStream[ConvId]()
  val onConversationLongClick: SourceStream[ConversationData] = EventStream[ConversationData]()

  protected val items = new ListBuffer[Item]
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
    DiffUtil.calculateDiff(new DiffCallback(items.toList, newItems), false).dispatchUpdatesTo(this)
    items.clear()
    items.appendAll(newItems)
  }

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position) match {
    case _: Item.IncomingRequests => IncomingViewType
    case _: Item.Header           => FolderViewType
    case _: Item.Conversation     => NormalViewType
  }

  override def getItemId(position: Int): Long = items(position) match {
    case Item.IncomingRequests(first, _)  => first.str.hashCode
    case Item.Header(id, _, _, _)         => id.str.hashCode
    case Item.Conversation(data, section) => (data.id.str + section.getOrElse("")).hashCode
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = viewType match {
    case IncomingViewType => ViewHolderFactory.newIncomingConversationRowViewHolder(this, parent)
    case FolderViewType   => ViewHolderFactory.newConversationFolderRowViewHolder(this, parent)
    case NormalViewType   => ViewHolderFactory.newNormalConversationRowViewHolder(this, parent)
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = {
    (items(position), holder) match {
      case (incomingRequests: Item.IncomingRequests, viewHolder: IncomingConversationRowViewHolder) =>
        val showSeparator = !this.isInstanceOf[ConversationFolderListAdapter]
        viewHolder.bind(incomingRequests, showSeparator)
      case (header: Item.Header, viewHolder: ConversationFolderRowViewHolder) =>
        viewHolder.bind(header, isFirst = position == 0)
      case (conversation: Item.Conversation, viewHolder: NormalConversationRowViewHolder) =>
        viewHolder.bind(conversation)
      case _ =>
        error(l"Invalid view holder/data pair")
    }
  }

  override def onClick(position: Int): Unit = items(position) match {
    case Item.IncomingRequests(first, _) => onConversationClick ! first
    case Item.Conversation(data, _)      => onConversationClick ! data.id
    case _                               =>
  }

  override def onLongClick(position: Int): Boolean = items(position) match {
    case Item.Conversation(data, _) =>
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

  sealed trait Item {
    val contentDescription: String
  }

  object Item {
    case class Header(id: FolderId, title: String, isExpanded: Boolean, unreadCount: Int = 0) extends Item {
      override val contentDescription: String = {
        s"$title (${if (isExpanded) "expanded" else "collapsed"})"
      }
    }

    case class Conversation(data: ConversationData, sectionTitle: Option[String] = None) extends Item {
      override val contentDescription: String = {
        val prefix = sectionTitle.map { t => s"$t: "}.getOrElse("")
        prefix + data.displayName.str
      }
    }

    case class IncomingRequests(first: ConvId, numberOfRequests: Int) extends Item {
      override val contentDescription: String = s"$numberOfRequests incoming request(s)"
    }
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

    def bind(item: Item.Conversation): Unit = {
      row.setConversation(item.data)
      row.setContentDescription(item.contentDescription)
    }
  }

  class IncomingConversationRowViewHolder(row: IncomingConversationListRow, listener: RowClickListener)
    extends ConversationRowViewHolder(row, listener) {

    def bind(item: Item.IncomingRequests, showSeparator: Boolean): Unit = {
      row.setIncoming(item.first, item.numberOfRequests)
      row.setContentDescription(item.contentDescription)
      row.setSeparatorVisibility(showSeparator)
    }
  }

  class ConversationFolderRowViewHolder(row: ConversationFolderListRow, listener: RowClickListener)
    extends ConversationRowViewHolder(row, listener) {

    def bind(item: Item.Header, isFirst: Boolean): Unit = {
      row.setTitle(item.title)
      row.setUnreadCount(item.unreadCount)
      row.setIsExpanded(item.isExpanded)
      row.setContentDescription(item.contentDescription)
      row.setIsFirstHeader(isFirst)
    }
  }

  object ViewHolderFactory extends DerivedLogTag {
    import ViewHelper.inflate

    def newNormalConversationRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): NormalConversationRowViewHolder = {
      val row = returning(inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)) { r =>
        r.setAlpha(1f)
        r.setMaxAlpha(adapter.maxAlpha)

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
        case (lhs: Header, rhs: Header) =>
          lhs.id == rhs.id
        case (lhs: Conversation, rhs: Conversation) =>
          lhs.data.id == rhs.data.id && lhs.sectionTitle == rhs.sectionTitle
        case (_: IncomingRequests, _: IncomingRequests) =>
          true
        case _ =>
          false
      }
    }

    override def areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
      (oldList(oldItemPosition), newList(newItemPosition)) match {
        case (Header(_, title, isExpanded, oldCount), Header(_, newTitle, newIsExpanded, newCount)) =>
          title == newTitle && isExpanded && newIsExpanded && oldCount == newCount
        case (Conversation(data, _), Conversation(newData, _)) =>
          data == newData
        case (IncomingRequests(_, requests), IncomingRequests(_, newRequests)) =>
          requests == newRequests
        case _ =>
          false
      }
  }
}
