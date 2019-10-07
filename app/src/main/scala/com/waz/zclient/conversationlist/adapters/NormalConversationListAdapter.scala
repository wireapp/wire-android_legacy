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

import com.waz.model.{ConvId, ConversationData}
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._

class NormalConversationListAdapter extends ConversationListAdapter {

  def setData(convs: Seq[ConversationData], incoming: Seq[ConvId]): Unit = {
    items = List.empty
    
    if (incoming.nonEmpty) {
      items = List(Item.IncomingRequests(incoming.head, incoming.size))
    }

    items ++= convs.map { data => Item.Conversation(data) }
    notifyDataSetChanged()
  }
}
