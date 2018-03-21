/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.api.scala

import com.waz.api._
import com.waz.model.ConvId
import com.waz.zclient.core.stores.conversation.IConversationStore

class ScalaConversationStore(zMessagingApi: ZMessagingApi) extends IConversationStore  {

  private val conversationsList = zMessagingApi.getConversations

  override def nextConversation(convId: ConvId): Option[ConvId] =
    if (conversationsList.size() <= 1) None else
      (0 until conversationsList.size()).find(i => conversationsList.get(i).getId == convId.str)
      .map { i => if (i == conversationsList.size() - 1)  conversationsList.get(i - 1) else  conversationsList.get(i + 1)}
      .map(ic => new ConvId(ic.getId))

  override def tearDown(): Unit = {}
}
