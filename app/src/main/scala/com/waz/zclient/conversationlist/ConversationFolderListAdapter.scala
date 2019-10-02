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
package com.waz.zclient.conversationlist

import android.view.ViewGroup
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, ConversationData, Name}
import com.waz.zclient.conversationlist.ConversationListAdapter._

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter extends ConversationListAdapter {
  import ConversationFolderListAdapter._

  // TODO: pull data from service when ready.
  private val folders = {
    val groups = Folder("Groups", List(ConversationItem("Android Team"), ConversationItem("iOS Team")))
    val contacts = Folder("Contacts", List(ConversationItem("San Pat"), ConversationItem("Bob")))
    List(groups, contacts)
  }

  private var items = Seq.empty[Item]
  updateItems()

  private def updateItems(): Unit = {
    items = folders.foldLeft(Seq.empty[Item]) { (result, folder) =>
      result ++ (HeaderItem(folder.title) :: folder.conversations)
    }
  }

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position) match {
    case _: HeaderItem => FolderViewType
    case _: ConversationItem => NormalViewType
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = {
    viewType match {
      case FolderViewType => ViewHolderFactory.newConversationFolderRowViewHolder(this, parent)
      case NormalViewType => ViewHolderFactory.newNormalConversationRowViewHolder(this, parent)
    }
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = {
    val model = items(position)

    holder match {
      case folderViewHolder: ConversationFolderRowViewHolder =>
        folderViewHolder.bind(model.asInstanceOf[HeaderItem])
      case normalViewHolder: NormalConversationRowViewHolder =>
        // TODO: adjust the view holder to accept the view model.
        val data = ConversationData(id = ConvId(), convType = ConversationType.OneToOne, generatedName = Name(model.asInstanceOf[ConversationItem].name))
        normalViewHolder.bind(data)
    }
  }
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[ConversationItem])

  sealed trait Item
  case class HeaderItem(title: String) extends Item
  case class ConversationItem(name: String) extends Item
}
