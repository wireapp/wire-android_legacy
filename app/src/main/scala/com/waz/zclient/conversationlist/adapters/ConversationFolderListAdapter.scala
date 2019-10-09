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
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.zclient.R
import com.waz.zclient.conversationlist.ConversationFolderListFragment.{FolderState, FoldersUiState}
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter.Folder._
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.utils.ContextUtils.getString

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter(implicit context: Context)
  extends ConversationListAdapter
    with DerivedLogTag {

  val onFolderStateChanged: SourceStream[FolderState] = EventStream[FolderState]()

  private var folders = Seq.empty[Folder]

  def setData(incoming: Seq[ConvId], groups: Seq[ConversationData], oneToOnes: Seq[ConversationData], folderStates: FoldersUiState): Unit = {
    var newItems = List.empty[Item]

    if (incoming.nonEmpty) {
      newItems ::= Item.IncomingRequests(incoming.head, incoming.size)
    }

    folders = calculateFolders(groups, oneToOnes, folderStates)

    newItems ++= folders.foldLeft(List.empty[Item]) { (acc, next) =>
      val header = Item.Header(next.id, next.title, next.isExpanded)
      val conversations = if (next.isExpanded) next.conversations else List.empty
      acc ++ (header :: conversations)
    }

    updateList(newItems)
  }

  private def calculateFolders(groups: Seq[ConversationData], oneToOnes: Seq[ConversationData], folderStates: FoldersUiState): Seq[Folder] = {
    val groupsFolder = {
      val isExpanded = folderStates.getOrElse(GroupId, true)
      createFolder(GroupId, R.string.conversation_folder_name_group, groups, isExpanded)
    }

    val oneToOnesFolder = {
      val isExpanded = folderStates.getOrElse(OneToOnesId, true)
      createFolder(OneToOnesId, R.string.conversation_folder_name_one_to_one, oneToOnes, isExpanded)
    }

    Seq(groupsFolder, oneToOnesFolder).flatten
  }

  private def createFolder(id: Uid, titleResId: Int, conversations: Seq[ConversationData], isExpanded: Boolean): Option[Folder] = {
    if (conversations.nonEmpty) Some(Folder(id, getString(titleResId), conversations, isExpanded))
    else None
  }

  override def onClick(position: Int): Unit = items(position) match {
    case header: Item.Header => collapseOrExpand(header, position)
    case _                   => super.onClick(position)
  }

  private def collapseOrExpand(header: Item.Header, headerPosition: Int): Unit = {
    if (header.isExpanded) collapseSection(header, headerPosition)
    else expandSection(header, headerPosition)
  }

  private def collapseSection(header: Item.Header, headerPosition: Int): Unit = {
    folder(header.id).fold() { folder =>
      updateHeader(header, headerPosition, isExpanded = false)

      val positionAfterHeader = headerPosition + 1
      val numberOfConversations = folder.conversations.size
      items.remove(positionAfterHeader, numberOfConversations)
      notifyItemRangeRemoved(positionAfterHeader, numberOfConversations)

      folder.isExpanded = false
      onFolderStateChanged ! FolderState(folder.id, folder.isExpanded)
    }
  }

  private def expandSection(header: Item.Header, headerPosition: Int): Unit = {
    folder(header.id).fold() { folder =>
      updateHeader(header, headerPosition, isExpanded = true)

      val positionAfterHeader = headerPosition + 1
      items.insertAll(positionAfterHeader, folder.conversations)
      notifyItemRangeInserted(positionAfterHeader, folder.conversations.size)

      folder.isExpanded = true
      onFolderStateChanged ! FolderState(folder.id, folder.isExpanded)
    }
  }

  private def folder(id: Uid): Option[Folder] = folders.find(_.id == id)

  private def updateHeader(header: Item.Header, position: Int, isExpanded: Boolean): Unit = {
    items.update(position, header.copy(isExpanded = isExpanded))
    notifyItemChanged(position)
  }
}

object ConversationFolderListAdapter {

  class Folder(val id: Uid, var title: String, val conversations: List[Item.Conversation], var isExpanded: Boolean = true)

  object Folder {

    val GroupId = Uid("Groups")
    val OneToOnesId = Uid("OneToOnes")

    def apply(id: Uid, title: String, conversations: Seq[ConversationData], isExpanded: Boolean): Folder = {
      new Folder(id, title, conversations.map(data => Item.Conversation(data)).toList, isExpanded)
    }
  }
}
