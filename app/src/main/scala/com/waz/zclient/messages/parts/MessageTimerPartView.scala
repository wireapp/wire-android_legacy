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
import com.waz.utils.returning
import com.waz.zclient.conversation.ConversationController._
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SystemMessageView, UsersController}
import com.waz.zclient.paintcode.HourGlassIcon
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class MessageTimerPartView (context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.MessageTimer

  inflate(R.layout.message_msg_timer_changed_content)

  val msgView = returning(findById[SystemMessageView](R.id.message_view))(_.setHasDivider(false))

  msgView.setIcon(HourGlassIcon(getColor(R.color.light_graphite)))

  (for {
    n <- message.map(_.userId).flatMap(inject[UsersController].displayName)
    d <- message.map(_.duration)
  } yield (n, d) match {
    case (Me, None)               => getString(R.string.you_turned_off_message_timer)
    case (Other(name), None)      => getString(R.string.other_turned_off_message_timer, name)
    case (Me, d@Some(_))          => getString(R.string.you_set_message_timer, getEphemeralDisplayString(d))
    case (Other(name), d@Some(_)) => getString(R.string.other_set_message_timer, name, getEphemeralDisplayString(d))
  }).onUi(msgView.setText)

}
