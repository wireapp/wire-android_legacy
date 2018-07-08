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
package com.waz.zclient.connect

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.{View, ViewGroup}
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversationlist.ConversationListAdapter.Incoming
import com.waz.zclient.utils.RichView
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog.ImplicitTag._

import scala.util.Try

class ConnectRequestAdapter(context: Context)(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[ConnectRequestAdapter.ViewHolder] with Injectable {
  import ConnectRequestAdapter._
  import com.waz.threading.Threading.Implicits.Background

  lazy val zms = inject[Signal[ZMessaging]]

  private var _incomingRequests = Seq.empty[UserId]

  lazy val incomingRequests = for {
    z             <- zms
    conversations <- z.convsStorage.convsSignal.map(_.conversations.filter(Incoming.filter).toSeq)
    members       <- Signal.sequence(conversations.map(c => z.membersStorage.activeMembers(c.id).map(_.find(_ != z.selfUserId))):_*)
  } yield members.flatten

  incomingRequests.onUi { v =>
    if (v != _incomingRequests) {
      _incomingRequests = v
      notifyDataSetChanged()
    }
  }

  override def getItemCount =
    _incomingRequests.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    new ViewHolder(new ConnectRequestRow(context))

  override def onBindViewHolder(holder: ViewHolder, position: Int) = {
    val view = holder.itemView.asInstanceOf[ConnectRequestRow]
    getItem(position).foreach { u =>
      view.setUser(u)
      view.acceptButton.onClick {
        removeItem(u)
        acceptConnectionRequest(u)
      }
      view.ignoreButton.onClick {
        removeItem(u)
        ignoreConnectionRequest(u)
      }
    }
  }

  private def removeItem(u: UserId) = {
    findPosition(u).foreach { p =>
      _incomingRequests = _incomingRequests.take(p) ++ _incomingRequests.drop(p + 1)
      notifyItemRemoved(p)
    }
  }

  override def getItemId(position: Int): Long =
    getItem(position).fold(position)(_.str.hashCode)

  def getItem(position: Int) = _incomingRequests.lift(position)

  def findPosition(user: UserId) = Try(_incomingRequests.indexOf(user)).toOption.filter(_ >= 0)

  private def acceptConnectionRequest(user: UserId) =
    for {
      z <- zms.head
      _ <- z.connection.acceptConnection(user)
    } yield {}

  private def ignoreConnectionRequest(user: UserId) =
    for {
      z <- zms.head
      _ <- z.connection.ignoreConnection(user)
    } yield {}
}

object ConnectRequestAdapter {
  class ViewHolder(v: View) extends RecyclerView.ViewHolder(v)
}
