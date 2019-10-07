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
import com.waz.model.{ConvId, ConversationData}
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

  def setData(incoming: Seq[ConvId], groups: Seq[ConversationData], oneToOnes: Seq[ConversationData]): Unit = {
    val folders = Seq(
      Folder(getString(R.string.conversation_folder_name_group), groups.map(data => Item.Conversation(data)).toList),
      Folder(getString(R.string.conversation_folder_name_one_to_one), oneToOnes.map(data => Item.Conversation(data)).toList)
    )

    items = folders.foldLeft(List.empty[Item]) { (result, folder) =>
      result ++ (Item.Header(folder.title) :: folder.conversations)
    }

    if (incoming.nonEmpty) {
      items ::= Item.IncomingRequests(incoming.head, incoming.size)
    }

    notifyDataSetChanged()
  }

  override def onClick(position: Int): Unit = items(position) match {
    case Item.Header(_) => // TODO: collapse logic
    case _              => super.onClick(position)
  }
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[Item.Conversation])
}
