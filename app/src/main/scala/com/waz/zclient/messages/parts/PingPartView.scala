/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SyncEngineSignals}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class PingPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Ping

  inflate(R.layout.message_ping_content)

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  val chatheadView: ChatheadView        = findById(R.id.chathead)
  val textViewMessage: TypefaceTextView = findById(R.id.ttv__row_conversation__ping_message)
  val glyphTextView: GlyphTextView      = findById(R.id.gtv__ping_icon)

  val locale = context.getResources.getConfiguration.locale

  val originalLeftPadding = context.getResources.getDimensionPixelSize(R.dimen.content__padding_left)

  val zMessaging = inject[Signal[ZMessaging]]
  val signals = inject[SyncEngineSignals]

  val message = Signal[MessageData]()

  val hotKnock = message.map(_.hotKnock)

  val userName = signals.userDisplayName(message).map(_.toUpperCase(locale))

  val text = userName.zip(hotKnock) map {
    case (name, false) => getString(R.string.content__xxx_pinged, name)
    case (name, true)  => getString(R.string.content__xxx_pinged_again, name)
  }

  message.map(_.userId) { chatheadView.setUserId }

  text.on(Threading.Ui) { t =>
    textViewMessage.setText(t)
    TextViewUtils.boldText(textViewMessage)
  }

  signals.userAccentColor(message).on(Threading.Ui) { c =>
    textViewMessage.setTextColor(c.getColor())
    glyphTextView.setTextColor(c.getColor())
  }

  // TODO: animate new ping, we need some generic controller to track message animations acrosss recycled views

  override def set(pos: Int, msg: MessageData, part: Option[MessageContent], widthHint: Int): Unit = {
    message.publish(msg, Threading.Ui)
  }
}
