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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{R, ViewHelper}

class ConversationStartPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_conversation_start_content)

  override val tpe: MsgPart = MsgPart.ConversationStart

  private val users = inject[UsersController]

  private val titleView = findById[TypefaceTextView](R.id.introduction_message)
  private val subtitleView = findById[TypefaceTextView](R.id.conversation_start_subtext)

  private val creator = message.map(_.userId).flatMap(users.displayName)
  private val subtitleText = creator map {
    case Me           => "You started the conversation"
    case Other(name)  => s"$name started the conversation"
  }

  subtitleText.onUi { subtitleView.setText }
  message.map(_.name).onUi { name => titleView.setText(name.getOrElse("")) }
}
