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

import java.util.Locale

import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.utils.events.{EventContext, EventStream, SourceStream}
import com.waz.zclient.{Injector, R}
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter.Folder._
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.utils.ContextUtils.getString

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter(implicit context: Context, eventContext: EventContext, injector: Injector)
  extends ConversationListAdapter
    with DerivedLogTag {

  val onFolderStateChanged: SourceStream[(FolderId, Boolean)] = EventStream()
  val onFoldersChanged: SourceStream[Set[FolderId]] = EventStream()

  private var folders = Seq.empty[Folder]

  def setData(incoming: Seq[ConvId],
              favorites: Seq[ConversationData],
              groups: Seq[ConversationData],
              oneToOnes: Seq[ConversationData],
              custom: Seq[(FolderData, Seq[ConversationData])],
              folderStates: Map[FolderId, Boolean]): Unit = {

    var newItems = List.empty[Item]

    if (incoming.nonEmpty) {
      newItems ::= Item.IncomingRequests(incoming.head, incoming.size)
    }

    val countMap = Map(
      FavoritesId -> favorites.count(_.hasUnreadMessages),
      GroupId     -> groups.count(_.hasUnreadMessages),
      OneToOnesId -> oneToOnes.count(_.hasUnreadMessages)
    ) ++ custom.map { case (folder, convs) => folder.id -> convs.count(_.hasUnreadMessages) }.toMap

    folders = calculateDefaultFolders(favorites, groups, oneToOnes) ++ calculateCustomFolders(custom)

    newItems ++= folders.foldLeft(List.empty[Item]) { (acc, next) =>
      val header = Item.Header(next.id, next.title, isExpanded = folderStates.getOrElse(next.id, true), unreadCount = countMap(next.id))
      val conversations = if (header.isExpanded) next.conversations.toList else List.empty
      acc ++ (header :: conversations)
    }

    onFoldersChanged ! folders.map(_.id).toSet

    updateList(newItems)
  }

  private def calculateDefaultFolders(favorites: Seq[ConversationData], groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Seq[Folder] = {
    val favoritesFolder = Folder(FavoritesId, getString(R.string.conversation_folder_name_favorites), favorites)
    val groupsFolder = Folder(GroupId, getString(R.string.conversation_folder_name_group), groups)
    val oneToOnesFolder = Folder(OneToOnesId, getString(R.string.conversation_folder_name_one_to_one), oneToOnes)
    Seq(favoritesFolder, groupsFolder, oneToOnesFolder).flatten
  }

  private def calculateCustomFolders(custom: Seq[(FolderData, Seq[ConversationData])]): Seq[Folder] =
    custom.flatMap { case (folderData, conversations) => Folder(folderData, conversations) }
          .sortBy(_.title.toLowerCase(Locale.getDefault))

  override def onClick(position: Int): Unit = items(position) match {
    case header: Item.Header => collapseOrExpand(header, position)
    case _                   => super.onClick(position)
  }

  private def collapseOrExpand(header: Item.Header, headerPosition: Int): Unit = {
    if (header.isExpanded) collapseSection(header, headerPosition)
    else expandSection(header, headerPosition)
  }

  private def collapseSection(header: Item.Header, headerPosition: Int): Unit = {
    if (header.isExpanded) {
      folderConversations(header.id).fold() { conversations =>
        updateHeader(header, headerPosition, isExpanded = false)
        items.remove(headerPosition + 1, conversations.size)
        notifyItemRangeRemoved(headerPosition + 1, conversations.size)
      }
    }
  }

  private def expandSection(header: Item.Header, headerPosition: Int): Unit = {
    if (!header.isExpanded) {
      folderConversations(header.id).fold() { conversations =>
        updateHeader(header, headerPosition, isExpanded = true)
        items.insertAll(headerPosition + 1, conversations)
        notifyItemRangeInserted(headerPosition + 1, conversations.size)
      }
    }
  }

  private def folderConversations(id: FolderId): Option[Seq[Item.Conversation]] = {
    folders.find(_.id == id).map(_.conversations)
  }

  private def updateHeader(header: Item.Header, position: Int, isExpanded: Boolean): Unit = {
    items.update(position, header.copy(isExpanded = isExpanded))
    notifyItemChanged(position)
    onFolderStateChanged ! (header.id, isExpanded)
  }
}

object ConversationFolderListAdapter {

  case class Folder(id: FolderId, title: String, conversations: Seq[Item.Conversation])

  object Folder {

    val FavoritesId = FolderId("Favorites")
    val GroupId = FolderId("Groups")
    val OneToOnesId = FolderId("OneToOnes")

    def apply(folderData: FolderData, conversations: Seq[ConversationData]): Option[Folder] = {
      Folder(folderData.id, folderData.name, conversations)
    }

    def apply(id: FolderId, title: String, conversations: Seq[ConversationData]): Option[Folder] = {
      if (conversations.isEmpty) None
      else Some(Folder(id, title, conversations.map(d => Item.Conversation(d, sectionTitle = Some(title)))))
    }
  }
}
