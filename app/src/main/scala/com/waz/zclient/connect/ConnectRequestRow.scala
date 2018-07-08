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
import android.util.AttributeSet
import android.widget.{FrameLayout, TextView}
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.{ChatheadView, UserDetailsView}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.{R, ViewHelper}

class ConnectRequestRow(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val user = Signal[UserId]()
  lazy val users = inject[UsersController]

  inflate(R.layout.fragment_connect_request_pending_inbox, this, addToParent = true)
  val ignoreButton = findById[ZetaButton](R.id.zb__connect_request__ignore_button)
  val acceptButton = findById[ZetaButton](R.id.zb__connect_request__accept_button)

  private val displayNameTextView = findById[TextView](R.id.ttv__connect_request__display_name)
  private val userDetailsView     = findById[UserDetailsView](R.id.udv__connect_request__user_details)
  private val chatheadView        = findById[ChatheadView](R.id.chathead)

  inject[AccentColorController].accentColor.map(_.getColor()).onUi { c =>
    ignoreButton.setIsFilled(false)
    ignoreButton.setAccentColor(c)
    ignoreButton.setTextColor(c)
    acceptButton.setAccentColor(c)
  }

  user.flatMap(users.user).onUi { u =>
    displayNameTextView.setText(u.name)
    TextViewUtils.boldText(displayNameTextView)
    userDetailsView.setUserId(u.id)
  }

  def setUser(user: UserId) = {
    this.user ! user
    chatheadView.setUserId(user)
  }
}
