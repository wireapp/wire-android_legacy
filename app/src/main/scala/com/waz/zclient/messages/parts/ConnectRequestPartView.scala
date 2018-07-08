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

import com.waz.utils.wrappers.AndroidURIUtil
import android.content.Context
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import com.waz.threading.Threading
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.{ChatheadView, UserDetailsView}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class ConnectRequestPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.ConnectRequest

  lazy val chathead     : ChatheadView    = findById(R.id.cv__row_conversation__connect_request__chat_head)
  lazy val label        : TextView        = findById(R.id.ttv__row_conversation__connect_request__label)
  lazy val userDetails  : UserDetailsView = findById(R.id.udv__row_conversation__connect_request__user_details)

  private val browser = inject[BrowserController]
  private val users   = inject[UsersController]

  val user = users.getOtherUser(message).collect { case Some(u) => u }
  user.map(_.id)(chathead.setUserId)
  user.map(_.id)(userDetails.setUserId)

  user.map(_.isAutoConnect).on(Threading.Ui) {
    case true =>
      label.setText(R.string.content__message__connect_request__auto_connect__footer)
      TextViewUtils.linkifyText(label, label.getCurrentTextColor, true, true, new Runnable() {
        override def run() = browser.openUrl(AndroidURIUtil parse getString(R.string.url__help))
      })
    case false =>
      label.setText(R.string.content__message__connect_request__footer)
  }

}
