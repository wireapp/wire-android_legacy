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
import android.widget.{LinearLayout, TextView}
import com.waz.threading.Threading
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController, SystemMessageView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class RenamePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Rename

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_rename_content)

  val users = inject[UsersController]

  val messageView: SystemMessageView  = findById(R.id.smv_header)
  val nameView: TextView              = findById(R.id.ttv__new_conversation_name)

  messageView.setIconGlyph(R.string.glyph__edit)

  val renamerName = message.map(_.userId).flatMap(users.displayName)

  val text = renamerName.map {
    case Me           => getString(R.string.content__system__you_renamed_conv)
    case Other(name)  => getString(R.string.content__system__other_renamed_conv, name)
  }.map(_.toUpperCase)

  text.on(Threading.Ui) { messageView.setText }

  message.map(_.name) { name =>
    nameView.setVisible(name.isDefined)
    nameView.setText(name.getOrElse(""))
  }

}
