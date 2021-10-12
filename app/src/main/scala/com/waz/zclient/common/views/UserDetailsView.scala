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
import com.wire.signals.Signal
import com.waz.zclient.messages.UsersController
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading._

class UserDetailsView(val context: Context, val attrs: AttributeSet, val defStyle: Int) extends LinearLayout(context, attrs, defStyle) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.user__details, this, addToParent = true)
  private lazy val userNameTextView: TextView = findById(R.id.user_handle)
  private lazy val userInfoTextView: TextView = findById(R.id.user_name)

  val users = inject[UsersController]
  val userId = Signal[UserId]()

  userId.flatMap(id => Signal.from(users.displayHandle(id))).onUi(userNameTextView.setText(_))
  userId.flatMap(users.user).map(_.name).onUi(userInfoTextView.setText(_))

  def setUserId(id: UserId): Unit =
    Option(id).fold(throw new IllegalArgumentException("UserId should not be null"))(userId ! _)

}
