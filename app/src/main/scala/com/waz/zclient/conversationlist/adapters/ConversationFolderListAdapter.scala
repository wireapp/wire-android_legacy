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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData, Uid}
import com.waz.zclient.R
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.utils.ContextUtils.getString

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter(implicit context: Context)
  extends ConversationListAdapter
    with DerivedLogTag {

  private var folders = Seq.empty[Folder]

  def setData(incoming: Seq[ConvId], groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Unit = {
    var newItems = List.empty[Item]

    if (incoming.nonEmpty) {
      newItems ::= Item.IncomingRequests(incoming.head, incoming.size)
    }

    folders = calculateFolders(groups, oneToOnes)

    newItems ++= folders.foldLeft(List.empty[Item]) { (acc, next) =>
      val header = Item.Header(next.id, next.title)
      val conversations = if (next.isExpanded) next.conversations else List.empty
      acc ++ (header :: conversations)
    }

    updateList(newItems)
  }

  private def calculateFolders(groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Seq[Folder] = {
    Seq(
      new Folder(Uid(), getString(R.string.conversation_folder_name_group), groups.map(data => Item.Conversation(data)).toList),
      new Folder(Uid(), getString(R.string.conversation_folder_name_one_to_one), oneToOnes.map(data => Item.Conversation(data)).toList)
    )
  }

  override def onClick(position: Int): Unit = items(position) match {
    case Item.Header(id, _) => folder(id).fold()(f => collapseOrExpand(f, position))
    case _                  => super.onClick(position)
  }

  private def folder(id: Uid): Option[Folder] = folders.find(_.id == id)

  private def collapseOrExpand(folder: Folder, position: Int): Unit = {
    if (folder.isExpanded) collapseSection(folder, position)
    else expandSection(folder, position)
  }

  private def collapseSection(folder: Folder, headerPosition: Int): Unit = {
    val positionAfterHeader = headerPosition + 1
    val numberToDrop = folder.conversations.size
    val (upToAndIncludingHeader, afterHeader) = items.splitAt(positionAfterHeader)
    items = upToAndIncludingHeader ++ afterHeader.drop(numberToDrop)
    notifyItemRangeRemoved(positionAfterHeader, numberToDrop)
    folder.isExpanded = false
  }

  private def expandSection(folder: Folder, headerPosition: Int): Unit = {
    val positionAfterHeader = headerPosition + 1
    val (upToAndIncludingHeader, afterHeader) = items.splitAt(positionAfterHeader)
    items = upToAndIncludingHeader ++ folder.conversations ++ afterHeader
    notifyItemRangeInserted(positionAfterHeader, folder.conversations.size)
    folder.isExpanded = true
  }
}

object ConversationFolderListAdapter {

  class Folder(val id: Uid, var title: String, val conversations: List[Item.Conversation], var isExpanded: Boolean = true)
}
