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
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.waz.model.ConversationData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{ConversationMembersSignal, UiStorage, UserSignal}
import com.waz.zclient.{Injectable, R, ViewHelper}

class SearchResultConversationRowView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ConversationRowView with ViewHelper with Injectable {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.conv_list_item)

  private var conversation: ConversationData = ConversationData.Empty
  private val conversationSignal = Signal[ConversationData]()
  private val nameView = findById[TypefaceTextView](R.id.conversation_title)
  private val avatar = findById[ConversationAvatarView](R.id.conversation_icon)
  private val subtitleView = findById[TypefaceTextView](R.id.conversation_subtitle)

  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val avatarInfo = for {
    z <- zms
    conv <- conversationSignal
    memberIds <- ConversationMembersSignal(conv.id)
    memberSeq <- Signal.sequence(memberIds.map(uid => UserSignal(uid)).toSeq:_*)
  } yield (conv.id, conv.convType, memberSeq.filter(_.id != z.selfUserId).map(_.id))

  subtitleView.setVisibility(View.GONE)
  avatarInfo.on(Threading.Ui) {
    case (convId, convType, members) =>
      avatar.setMembers(members, convId, convType)
  }

  def getConversation: ConversationData = conversation

  def setConversation(conversationData: ConversationData): Unit = {
    nameView.setText(conversationData.displayName)
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
