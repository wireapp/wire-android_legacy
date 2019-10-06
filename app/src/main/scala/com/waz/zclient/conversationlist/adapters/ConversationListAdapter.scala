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

import android.support.v7.widget.RecyclerView
import android.view.View.OnLongClickListener
import android.view.{View, ViewGroup}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, ConversationData}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.conversationlist.adapters.ConversationFolderListAdapter.HeaderItem
import com.waz.zclient.conversationlist.adapters.ConversationListAdapter.ConversationRowViewHolder
import com.waz.zclient.conversationlist.views.{ConversationFolderListRow, IncomingConversationListRow, NormalConversationListRow}
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.{R, ViewHelper}

abstract class ConversationListAdapter extends RecyclerView.Adapter[ConversationRowViewHolder] with DerivedLogTag {

  val onConversationClick: SourceStream[ConvId] = EventStream[ConvId]()
  val onConversationLongClick: SourceStream[ConversationData] = EventStream[ConversationData]()

  protected var maxAlpha = 1.0f

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
    notifyDataSetChanged()
  }
}

object ConversationListAdapter {

  val NormalViewType = 0
  val IncomingViewType = 1
  val FolderViewType = 2

  trait ConversationRowViewHolder extends RecyclerView.ViewHolder

  case class NormalConversationRowViewHolder(view: NormalConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(conversation: ConversationData): Unit =
      view.setConversation(conversation)
  }

  case class IncomingConversationRowViewHolder(view: IncomingConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(first: ConvId, numberOfRequest: Int): Unit = {
      view.setIncoming(first, numberOfRequest)
    }
  }

  case class ConversationFolderRowViewHolder(view: ConversationFolderListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(header: HeaderItem, isFirst: Boolean): Unit = {
      view.setTitle(header.title)
      view.setIsFirstHeader(isFirst)
    }
  }

  object ViewHolderFactory extends DerivedLogTag {
    import ViewHelper.inflate

    def newNormalConversationRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): NormalConversationRowViewHolder = {
      val row = returning(inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)) { r =>
        r.setAlpha(1f)
        r.setMaxAlpha(adapter.maxAlpha)

        r.setOnClickListener(new View.OnClickListener {
          override def onClick(view: View): Unit =
            r.conversationData.map(_.id).foreach(adapter.onConversationClick ! _)
        })

        r.setOnLongClickListener(new OnLongClickListener {
          override def onLongClick(view: View): Boolean = {
            r.conversationData.foreach(adapter.onConversationLongClick ! _)
            true
          }
        })

        r.setConversationCallback(new ConversationCallback {
          override def onConversationListRowSwiped(convId: String, view: View): Unit =
            r.conversationData.foreach(adapter.onConversationLongClick ! _)
        })
      }

      NormalConversationRowViewHolder(row)
    }

    def newIncomingConversationRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): IncomingConversationRowViewHolder = {
      val row = returning(inflate[IncomingConversationListRow](R.layout.incoming_conv_list_item, parent, addToParent = false)) { r =>
        r.setOnClickListener(new View.OnClickListener {
          override def onClick(view: View): Unit = r.firstIncomingConversation.foreach(adapter.onConversationClick ! _)
        })
      }

      IncomingConversationRowViewHolder(row)
    }

    def newConversationFolderRowViewHolder(adapter: ConversationListAdapter, parent: ViewGroup): ConversationFolderRowViewHolder = {
      val row = inflate[ConversationFolderListRow](R.layout.conv_folder_list_item, parent, addToParent = false)
      ConversationFolderRowViewHolder(row)
    }
  }
}
