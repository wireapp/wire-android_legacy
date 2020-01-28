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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.waz.model.{ConversationRole, UserId}
import com.waz.zclient.R
import com.waz.zclient.ui.text.TypefaceTextView

class UnconnectedParticipantAdapter(userId:      UserId,
                                    isGuest:     Boolean,
                                    isExternal:  Boolean,
                                    isDarkTheme: Boolean,
                                    isGroup:     Boolean,
                                    isWireless:  Boolean,
                                    userName:    String,
                                    userHandle:  String)(implicit context: Context)
  extends BaseSingleParticipantAdapter(userId, isGuest, isExternal, isDarkTheme, isGroup, isWireless) {
  import BaseSingleParticipantAdapter._
  import UnconnectedParticipantAdapter._


  def set(timerText:       Option[String],
          participantRole: Option[ConversationRole] = None,
          selfRole:        Option[ConversationRole] = None): Unit = {
    this.timerText       = timerText
    this.participantRole = participantRole
    this.selfRole        = selfRole
    notifyDataSetChanged()
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.participant_user_name_row, parent,false)
      UserNameViewHolder(view)
    case _ =>
      super.onCreateViewHolder(parent, viewType)
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = holder match {
    case h: UserNameViewHolder => h.bind(userName, userHandle)
    case _                     => super.onBindViewHolder(holder, position)
  }

  override def getItemCount: Int = super.getItemCount + 1

  override def getItemId(position: Int): Long = getItemViewType(position) match {
    case Header     => 0L
    case GroupAdmin => 1L
    case UserName   => 2L
  }

  override def getItemViewType(position: Int): Int =
    if (position == 0) UserName
    else if (position == 2 && isGroupAdminViewVisible) GroupAdmin
    else Header
}

object UnconnectedParticipantAdapter {
  case class UserNameViewHolder(view: View) extends ViewHolder(view) {
    private lazy val userName   = view.findViewById[TypefaceTextView](R.id.user_name)
    private lazy val userHandle = view.findViewById[TypefaceTextView](R.id.user_handle)

    def bind(userName: String, userHandle: String): Unit = {
      this.userName.setText(userName)
      this.userHandle.setText(userHandle)
      view.setContentDescription(s"User: $userName")
    }
  }
}
