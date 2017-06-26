/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.adapters

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.View.OnLongClickListener
import android.view.{View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose}
import com.waz.api.IConversation
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.adapters.ConversationListAdapter._
import com.waz.zclient.controllers.UserAccountsController
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.views.conversationlist.{IncomingConversationListRow, NormalConversationListRow}
import com.waz.zclient.{Injectable, Injector, R}

class ConversationListAdapter(context: Context)(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[ConversationRowViewHolder] with Injectable {

  setHasStableIds(true)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val userAccountsController = inject[UserAccountsController]
  val currentMode = Signal[ListMode]()
  lazy val conversationsSignal = for {
    z <- zms
    conversations <- z.convsStorage.convsSignal
    mode <- currentMode
  } yield
    conversations.conversations
      .filter{ conversationData =>
        mode.filter(conversationData)
      }
      .toSeq
      .sorted(mode.sort)

  var conversations = Seq.empty[ConversationData]
  var incomingRequests = (Seq.empty[ConversationData], Seq.empty[UserId])

  lazy val incomingRequestsSignal = for {
    z <- zms
    conversations <- z.convsStorage.convsSignal.map(_.conversations.filter(Incoming.filter).toSeq)
    members <- Signal.sequence(conversations.map(c => z.membersStorage.activeMembers(c.id).map(_.find(_ != z.selfUserId))):_*)
    mode <- currentMode
  } yield if (mode == Normal) (conversations, members.flatten) else (Seq(), Seq())

  val onConversationClick = EventStream[ConversationData]()
  val onConversationLongClick = EventStream[ConversationData]()

  var maxAlpha = 1.0f

  Signal(conversationsSignal, incomingRequestsSignal).on(Threading.Ui) {
    case (convs, requests) =>
      conversations = convs
      incomingRequests = requests
      notifyDataSetChanged()
  }

  private def getConversation(position: Int): Option[ConversationData] = {
    conversations.lift(position)
  }

  private def getItem(position: Int): Option[ConversationData] =
    incomingRequests._2 match {
      case Seq() => getConversation(position)
      case _ => if (position == 0) None else getConversation(position - 1)
    }

  override def getItemCount = {
    val incoming = if (incomingRequests._2.nonEmpty) 1 else 0
    conversations.size + incoming
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
        incomingViewHolder.bind(incomingRequests)
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int) = {
    viewType match {
      case NormalViewType =>
        NormalConversationRowViewHolder(returning(new NormalConversationListRow(context)) { r =>
          r.setAlpha(1f)
          r.setMaxAlpha(maxAlpha)
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = {
              Option(view.getTag.asInstanceOf[ConversationData]).foreach { onConversationClick ! _ }
            }
          })
          r.setOnLongClickListener(new OnLongClickListener {
            override def onLongClick(view: View): Boolean = {
              Option(view.getTag.asInstanceOf[ConversationData]).foreach { onConversationLongClick ! _ }
              true
            }
          })
          r.setConversationCallback(new ConversationCallback {
            override def onConversationListRowLongClicked(conversation: IConversation, view: View) = {
              Option(view.getTag.asInstanceOf[ConversationData]).foreach { onConversationLongClick ! _ }
            }
            override def onConversationListRowSwiped(conversation: IConversation, view: View) = {
              Option(view.getTag.asInstanceOf[ConversationData]).foreach { onConversationLongClick ! _ }
            }
          })
        })
      case IncomingViewType =>
        IncomingConversationRowViewHolder(returning(new IncomingConversationListRow(context)) { r =>
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = {
              incomingRequests._1.headOption.foreach(onConversationClick ! _ )
            }
          })
        })
    }
  }

  override def getItemId(position: Int): Long =
    getItem(position).fold(position)(_.id.str.hashCode)

  override def getItemViewType(position: Int): Int =
    if (position == 0 && incomingRequests._2.nonEmpty)
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
    override val filter = (c: ConversationData) =>
      Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && !c.archived
  }
  case object Archive extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = (c: ConversationData) =>
      Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.Incoming, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && c.archived
  }
  case object Incoming extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = (c: ConversationData) =>
      c.convType == ConversationType.Incoming && !c.hidden
  }

  trait ConversationRowViewHolder extends RecyclerView.ViewHolder

  case class NormalConversationRowViewHolder(view: NormalConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder{
    def bind(conversation: ConversationData): Unit = {
      view.setTag(conversation)
      view.setConversation(conversation)
    }
  }

  case class IncomingConversationRowViewHolder(view: IncomingConversationListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder{
    def bind(convsAndUsers: (Seq[ConversationData], Seq[UserId])): Unit = {
      convsAndUsers._1.headOption.foreach(view.setTag)
      view.setIncomingUsers(convsAndUsers._2)
    }
  }
}
