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
import com.waz.model.{ConvId, ConversationData}
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter._
import com.waz.zclient.log.LogUI._

class NormalConversationListAdapter extends ConversationListAdapter {

  setHasStableIds(true)

  private var conversations = Seq.empty[ConversationData]
  private var incomingRequests = Seq.empty[ConvId]

  def setData(convs: Seq[ConversationData], incoming: Seq[ConvId]): Unit = {
    conversations = convs
    incomingRequests = incoming
    notifyDataSetChanged()
  }

  // Getters

  private def getConversation(position: Int): Option[ConversationData] = conversations.lift(position)

  private def getItem(position: Int): Option[ConversationData] = incomingRequests match {
    case Seq() => getConversation(position)
    case _ => if (position == 0) None else getConversation(position - 1)
  }

  override def getItemCount: Int =
    if (hasIncomingRequests) conversations.size + 1
    else conversations.size

  override def getItemId(position: Int): Long = getItem(position).fold(position)(_.id.str.hashCode)

  override def getItemViewType(position: Int): Int =
    if (position == 0 && hasIncomingRequests) IncomingViewType
    else NormalViewType

  private def hasIncomingRequests: Boolean = incomingRequests.nonEmpty

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
      // TODO: don't force unwrap
      incomingViewHolder.bind(incomingRequests.head, incomingRequests.size)
  }
}
