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
import com.waz.model.ConvId
import com.waz.utils.events.EventContext
import com.waz.zclient.Injector
import com.waz.zclient.conversationlist.ConversationListController.NamedConversation
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._

class NormalConversationListAdapter(implicit context: Context, eventContext: EventContext, injector: Injector)
  extends ConversationListAdapter {

  def setData(convs: Seq[NamedConversation], incoming: Seq[ConvId]): Unit =
    updateList(
      (if (incoming.nonEmpty)
        List(Item.IncomingRequests(incoming.head, incoming.size))
       else
        Nil
      ) ++ convs.map(Item.Conversation(_))
    )
}
