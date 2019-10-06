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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData}
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter._
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._

/**
  * A list adapter for displaying conversations grouped into folders.
  */
class ConversationFolderListAdapter extends ConversationListAdapter with DerivedLogTag {

  override protected var items: List[Item] = List.empty

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
}

object ConversationFolderListAdapter {

  case class Folder(title: String, conversations: List[Item.Conversation])
}
