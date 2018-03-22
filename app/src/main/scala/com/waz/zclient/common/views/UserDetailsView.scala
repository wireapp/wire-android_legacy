/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.waz.zclient.common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import com.waz.model.UserId
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.UsersController
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{R, ViewHelper}

class UserDetailsView(val context: Context, val attrs: AttributeSet, val defStyle: Int) extends LinearLayout(context, attrs, defStyle) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private lazy val userNameTextView: TextView = findById(R.id.user_handle)
  private lazy val userInfoTextView: TextView = findById(R.id.ttv__user_details__user_info)
  inflate(R.layout.user__details, this, addToParent = true)

  val users = inject[UsersController]
  val userId = Signal[UserId]()

  userId.flatMap(users.userHandle).map {
    case Some(h) => StringUtils.formatHandle(h.string)
    case None => ""
  }.on(Threading.Ui) { userNameTextView.setText }

  (for {
    id <- userId
    c <- users.userFirstContact(id)
    n <- users.displayNameString(id)
  } yield {
      c match {
        case Some(cont) if cont.name == n => getContext.getString(R.string.content__message__connect_request__user_info, "")
        case Some(cont) => getContext.getString(R.string.content__message__connect_request__user_info, c.get.name)
        case _ => ""
      }
  }).on(Threading.Ui) { userInfoTextView.setText }

  def setUserId(id: UserId): Unit =
    Option(id).fold(throw new IllegalArgumentException("UserId should not be null"))(userId ! _)

}
