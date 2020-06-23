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
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Name
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SystemMessageView, UsersController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.log.LogUI._

class RestrictedFilePartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Rename

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_restricted_file_content)

  private lazy val users = inject[UsersController]
  private lazy val accentColor = inject[AccentColorController].accentColor

  private val messageView: SystemMessageView = findById(R.id.smv_header)

  messageView.setIconGlyph(R.string.glyph__error)

  private val text = message.flatMap(m => users.displayName(m.userId).map((_, m.name))).map {
    case (Me, Some(Name(ext))) => getString(R.string.file_restrictions__sender_error, ext)
    case (Other(name), _)      => getString(R.string.file_restrictions__receiver_error, name)
    case _ =>
      warn(l"Unable to display the error message for restricted file: no username and extension")
      ""
  }

  Signal(text, accentColor).onUi {
    case (txt, color) => messageView.setTextWithLink(txt, color.color) { }
  }
}
