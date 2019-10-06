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

import android.view.ViewGroup
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData}
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.log.LogUI._

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter extends ConversationListAdapter with DerivedLogTag {

  setHasStableIds(true)

  private var items = List.empty[Item]

  def setData(incoming: Seq[ConvId], groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Unit = {
    val folders = Seq(
      Folder("Groups", groups.map(data => Item.Conversation(data)).toList),
      Folder("One to One", oneToOnes.map(data => Item.Conversation(data)).toList)
    )

    items = folders.foldLeft(List.empty[Item]) { (result, folder) =>
      result ++ (Item.Header(folder.title) :: folder.conversations)
    }

    if (incoming.nonEmpty) {
      items ::= Item.IncomingRequests(incoming.head, incoming.size)
    }

    notifyDataSetChanged()
  }

  // Getters

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position) match {
    case _: Item.IncomingRequests => IncomingViewType
    case _: Item.Header           => FolderViewType
    case _: Item.Conversation     => NormalViewType
  }

  override def getItemId(position: Int): Long = items(position) match {
    case Item.IncomingRequests(first, _) => first.str.hashCode
    case Item.Header(title)              => title.hashCode
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
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[Item.Conversation])
}
