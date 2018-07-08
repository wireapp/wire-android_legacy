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
package com.waz.zclient.appentry

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.EmailAddress
import com.waz.utils.events.EventContext
import com.waz.zclient.appentry.InvitesAdapter._
import com.waz.zclient.appentry.controllers.InvitationsController
import com.waz.zclient.appentry.controllers.InvitationsController.Sent
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{Injectable, Injector, R}

import scala.collection.immutable.ListMap


class InvitesAdapter()(implicit inj: Injector, eventContext: EventContext, context: Context) extends RecyclerView.Adapter[InviteViewHolder] with Injectable {

  private val invitesController = inject[InvitationsController]

  private var _invitations = ListMap[EmailAddress, InvitationsController.InvitationStatus]()

  invitesController.invitations.onUi { invitations =>
    _invitations = invitations
    notifyDataSetChanged()
  }

  def getItem(pos: Int): Option[(EmailAddress, InvitationsController.InvitationStatus)] =
    _invitations.toSeq.lift(pos - 1)

  override def getItemCount: Int = _invitations.size + 1

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteViewHolder = {
    val viewId = if (viewType == 0) R.layout.invite_list_header else R.layout.sent_invite_layout
    InviteViewHolder(LayoutInflater.from(parent.getContext).inflate(viewId, parent, false))
  }

  override def onBindViewHolder(holder: InviteViewHolder, position: Int): Unit =
    getItem(position).foreach { case (email, status) => holder.bind(email, status) }

  override def getItemId(position: Int): Long = getItem(position).fold(0)(_._1.str.hashCode)

  override def getItemViewType(position: Int): Int = if (position == 0) 0 else 1
}

object InvitesAdapter {
  case class InviteViewHolder(view: View) extends RecyclerView.ViewHolder(view) {

    private val emailText = ViewUtils.getView[TypefaceTextView](view, R.id.invited_email_text)
    private val statusIcon = ViewUtils.getView[GlyphTextView](view, R.id.status_icon)

    def bind(email: EmailAddress, status: InvitationsController.InvitationStatus): Unit = {
      emailText.setText(email.str)
      statusIcon.setText(if (status == Sent) R.string.glyph__check else R.string.empty_string)
    }
  }
}
