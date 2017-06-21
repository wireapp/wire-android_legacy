/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
import com.waz.model.MessageContent
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.returning
import com.waz.zclient.controllers.ThemeController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class InviteBannerPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.InviteBanner

  private val themeController = inject[ThemeController]
  private val pickUserController = inject[IPickUserController]

  lazy val showContactsButton: ZetaButton =
    returning(findById[ZetaButton](R.id.zb__conversation__invite_banner__show_contacts)) { button =>
      button.onClick {
        pickUserController.showPickUser(IPickUserController.Destination.CURSOR, null)
      }
    }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit =
    showContactsButton.setIsFilled(themeController.isDarkTheme)
}
