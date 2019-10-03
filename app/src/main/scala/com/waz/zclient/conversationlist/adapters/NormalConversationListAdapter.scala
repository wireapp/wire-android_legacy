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

import android.view.ViewGroup
import com.waz.model.{ConversationData, UserId}
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.log.LogUI._

class NormalConversationListAdapter extends ConversationListAdapter {

  setHasStableIds(true)

  private var conversations = Seq.empty[ConversationData]
  private var incomingRequests = (Seq.empty[ConversationData], Seq.empty[UserId])

  def setData(convs: Seq[ConversationData], incoming: (Seq[ConversationData], Seq[UserId])): Unit = {
    conversations = convs
    incomingRequests = incoming
    notifyDataSetChanged()
  }

  // Getters

  private def getConversation(position: Int): Option[ConversationData] = conversations.lift(position)

  private def getItem(position: Int): Option[ConversationData] = incomingRequests._2 match {
    case Seq() => getConversation(position)
    case _ => if (position == 0) None else getConversation(position - 1)
  }

  override def getItemCount: Int = {
    val incoming = if (incomingRequests._2.nonEmpty) 1 else 0
    conversations.size + incoming
  }

  override def getItemId(position: Int): Long = getItem(position).fold(position)(_.id.str.hashCode)

  override def getItemViewType(position: Int): Int =
    if (position == 0 && incomingRequests._2.nonEmpty) IncomingViewType
    else NormalViewType

  // View management

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = viewType match {
    case NormalViewType => ViewHolderFactory.newNormalConversationRowViewHolder(this, parent)
    case IncomingViewType => ViewHolderFactory.newIncomingConversationRowViewHolder(this, parent)
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int): Unit = holder match {
    case normalViewHolder: NormalConversationRowViewHolder =>
      getItem(position).fold(error(l"Conversation not found at position: $position")) { item =>
        normalViewHolder.bind(item)
      }
    case incomingViewHolder: IncomingConversationRowViewHolder =>
      incomingViewHolder.bind(incomingRequests)
  }
}
