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
package com.waz.zclient.conversationlist

import android.support.v7.widget.RecyclerView
import android.view.View.OnLongClickListener
import android.view.{View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversationlist.ConversationListAdapter._
import com.waz.zclient.conversationlist.views.{IncomingConversationListRow, NormalConversationListRow}
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

class ConversationListAdapter(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[ConversationRowViewHolder] with Injectable {

  setHasStableIds(true)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val userAccountsController = inject[UserAccountsController]

  var _conversations = Seq.empty[ConversationData]
  var _incomingRequests = (Seq.empty[ConversationData], Seq.empty[UserId])

  val onConversationClick = EventStream[ConvId]()
  val onConversationLongClick = EventStream[ConversationData]()

  var maxAlpha = 1.0f

  def setData(convs: Seq[ConversationData], incoming: (Seq[ConversationData], Seq[UserId])): Unit = {
    _conversations = convs
    _incomingRequests = incoming
    verbose(s"Conversation list updated => conversations: ${convs.size}, requests: ${incoming._2.size}")
    notifyDataSetChanged()
  }

  private def getConversation(position: Int): Option[ConversationData] =
    _conversations.lift(position)

  private def getItem(position: Int): Option[ConversationData] =
    _incomingRequests._2 match {
      case Seq() => getConversation(position)
      case _ => if (position == 0) None else getConversation(position - 1)
    }

  override def getItemCount = {
    val incoming = if (_incomingRequests._2.nonEmpty) 1 else 0
    _conversations.size + incoming
  }

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int) = {
    holder match {
      case normalViewHolder: NormalConversationRowViewHolder =>
        getItem(position).fold {
          error(s"Conversation not found at position: $position")
        } { item =>
          normalViewHolder.bind(item)
        }
      case incomingViewHolder: IncomingConversationRowViewHolder =>
        incomingViewHolder.bind(_incomingRequests)
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int) = {
    viewType match {
      case NormalViewType =>
        NormalConversationRowViewHolder(returning(ViewHelper.inflate[NormalConversationListRow](R.layout.normal_conv_list_item, parent, addToParent = false)) { r =>
          r.setAlpha(1f)
          r.setMaxAlpha(maxAlpha)
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit =
              r.conversationData.map(_.id).foreach(onConversationClick ! _ )
          })
          r.setOnLongClickListener(new OnLongClickListener {
            override def onLongClick(view: View): Boolean = {
              r.conversationData.foreach(onConversationLongClick ! _)
              true
            }
          })
          r.setConversationCallback(new ConversationCallback {
            override def onConversationListRowSwiped(convId: String, view: View) =
              r.conversationData.foreach(onConversationLongClick ! _)
          })
        })
      case IncomingViewType =>
        IncomingConversationRowViewHolder(returning(ViewHelper.inflate[IncomingConversationListRow](R.layout.incoming_conv_list_item, parent, addToParent = false)) { r =>
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit =
              _incomingRequests._1.headOption.map(_.id).foreach(onConversationClick ! _ )
          })
        })
    }
  }

  override def getItemId(position: Int): Long =
    getItem(position).fold(position)(_.id.str.hashCode)

  override def getItemViewType(position: Int): Int =
    if (position == 0 && _incomingRequests._2.nonEmpty)
      IncomingViewType
    else
      NormalViewType

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
    notifyDataSetChanged()
  }
}

object ConversationListAdapter {

  val NormalViewType = 0
  val IncomingViewType = 1

  trait ListMode {
    val nameId: Int
    val filter: (ConversationData) => Boolean
    val sort = ConversationData.ConversationDataOrdering
  }

  case object Normal extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__title
    override val filter = ConversationListController.RegularListFilter
  }

  case object Archive extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.ArchivedListFilter
  }

  case object Incoming extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.IncomingListFilter
  }

  case object Integration extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.IntegrationFilter
  }

  trait ConversationRowViewHolder extends RecyclerView.ViewHolder

  case class NormalConversationRowViewHolder(view: NormalConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(conversation: ConversationData): Unit =
      view.setConversation(conversation)
  }

  case class IncomingConversationRowViewHolder(view: IncomingConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(convsAndUsers: (Seq[ConversationData], Seq[UserId])): Unit =
      view.setIncomingUsers(convsAndUsers._2)
  }
}
