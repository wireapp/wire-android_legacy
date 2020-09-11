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
package com.waz.zclient.usersearch.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.waz.model.ConversationData
import com.waz.service.ZMessaging
import com.wire.signals.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{ConversationMembersSignal, UiStorage, UserSignal}
import com.waz.zclient.{Injectable, R, ViewHelper}
import com.waz.threading.Threading._

class SearchResultConversationRowView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr)
    with ConversationRowView with ViewHelper with Injectable {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.conv_list_item)

  private var conversation: ConversationData = ConversationData.Empty
  private val conversationSignal = Signal[ConversationData]()
  private val nameView = findById[TypefaceTextView](R.id.conversation_title)
  private val avatar = findById[ConversationAvatarView](R.id.conversation_icon)
  private val subtitleView = findById[TypefaceTextView](R.id.conversation_subtitle)

  private lazy val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  (for {
    z         <- zms
    conv      <- conversationSignal
    memberIds <- ConversationMembersSignal(conv.id)
    memberSeq <- Signal.sequence(memberIds.map(uid => UserSignal(uid)).toSeq:_*)
    isGroup   <- Signal.from(z.conversations.isGroupConversation(conv.id))
  } yield (conv.id, isGroup, memberSeq.filter(_.id != z.selfUserId), z.teamId)).onUi {
    case (convId, isGroup, members, selfTeamId) =>
      avatar.setMembers(members, convId, isGroup, selfTeamId)
  }

  (for {
    conv <- conversationSignal
    name <- inject[ConversationController].conversationName(conv.id)
  } yield name).onUi { name => nameView.setText(name.str)}

  subtitleView.setVisibility(View.GONE)

  def getConversation: ConversationData = conversation

  def setConversation(conversationData: ConversationData): Unit = {
    if (this.conversation.id != conversationData.id) {
      avatar.clearImages()
      avatar.setConversationType(conversationData.convType)
      conversationSignal ! conversationData
    }
    this.conversation = conversationData
  }

  def applyDarkTheme(): Unit = {
    nameView.setTextColor(ContextCompat.getColor(getContext, R.color.text__primary_dark))
  }
}
