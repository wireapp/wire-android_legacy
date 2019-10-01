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

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, ConversationData, Name}
import com.waz.zclient.conversationlist.ConversationListAdapter.{ConversationRowViewHolder, NormalConversationRowViewHolder}
import com.waz.zclient.conversationlist.views.{ConversationFolderListRow, NormalConversationListRow}
import com.waz.zclient.{R, ViewHelper}

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter extends RecyclerView.Adapter[ConversationRowViewHolder] with DerivedLogTag {
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
    items = Seq.empty
    folders.foreach { section => items ++= HeaderItem(section.title) :: section.conversations }
  }

  override def getItemCount: Int = items.size

  override def getItemViewType(position: Int): Int = items(position).viewType

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = {
    viewType match {
      case ViewType.Folder =>
        val view = ViewHelper.inflate[ConversationFolderListRow](R.layout.conv_folder_list_item, parent, addToParent = false)
        ConversationFolderRowViewHolder(view)
      case ViewType.Conversation =>
        val view = ViewHelper.inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)
        NormalConversationRowViewHolder(view)
    }
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = {
    val model = items(position)

    model.viewType match {
      case ViewType.Folder =>
        holder.asInstanceOf[ConversationFolderRowViewHolder].bind(model.asInstanceOf[HeaderItem])
      case ViewType.Conversation =>
        // TODO: adjust the view holder to accept the view model.
        val data = ConversationData(id = ConvId(), convType = ConversationType.OneToOne, generatedName = Name(model.asInstanceOf[ConversationItem].name))
        holder.asInstanceOf[NormalConversationRowViewHolder].bind(data)
    }
  }
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[ConversationItem])

  sealed trait Item {
    val viewType: Int
  }

  case class HeaderItem(title: String) extends Item {
    override val viewType: Int = ViewType.Folder
  }

  case class ConversationItem(name: String) extends Item {
    override val viewType: Int = ViewType.Conversation
  }

  object ViewType {
    val Folder = 0
    val Conversation = 1
  }

  case class ConversationFolderRowViewHolder(view: ConversationFolderListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(sectionHeader: HeaderItem): Unit =
      view.setTitle(sectionHeader.title)
  }
}
