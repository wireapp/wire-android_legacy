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
import com.waz.model.ConversationData
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.log.LogUI._

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter extends ConversationListAdapter with DerivedLogTag {

  setHasStableIds(true)

  private var folders = Seq.empty[Folder]
  private var items = Seq.empty[Item]

  def setData(groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Unit = {
    folders = Seq(
      Folder("Groups", groups.map(data => ConversationItem(data)).toList),
      Folder("One to One", oneToOnes.map(data => ConversationItem(data)).toList)
    )

    updateItems()
  }

  private def updateItems(): Unit = {
    items = folders.foldLeft(Seq.empty[Item]) { (result, folder) =>
      result ++ (HeaderItem(folder.title) :: folder.conversations)
    }

    notifyDataSetChanged()
  }

  // Getters

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position) match {
    case _: HeaderItem => FolderViewType
    case _: ConversationItem => NormalViewType
  }

  override def getItemId(position: Int): Long = items(position) match {
    case HeaderItem(title) => title.hashCode
    case ConversationItem(data) => data.id.str.hashCode
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = viewType match {
    case FolderViewType => ViewHolderFactory.newConversationFolderRowViewHolder(this, parent)
    case NormalViewType => ViewHolderFactory.newNormalConversationRowViewHolder(this, parent)
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = {
    (holder, items(position)) match {
      case (viewHolder: ConversationFolderRowViewHolder, header: HeaderItem) =>
        viewHolder.bind(header)
      case (viewHolder: NormalConversationRowViewHolder, conversation: ConversationItem) =>
        viewHolder.bind(conversation.data)
      case _ =>
        error(l"Invalid view holder/data pair")
    }
  }
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[ConversationItem])

  sealed trait Item
  case class HeaderItem(title: String) extends Item

  // TODO: It's not necessary to provide all of this data. Will refactor.
  case class ConversationItem(data: ConversationData) extends Item
}
