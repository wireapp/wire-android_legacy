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
import com.waz.api.Message.Type._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SystemMessageView, UsersController}
import com.waz.zclient.paintcode.ViewWithColor
import com.waz.zclient.utils.ContextUtils.{getColor, getString}
import com.waz.zclient.{R, ViewHelper}

class ReadReceiptsPartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with MessageViewPart
    with ViewHelper
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  def tpe: MsgPart = MsgPart.ReadReceipts

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_readreceipts_content)

  private lazy val view = returning(findById[SystemMessageView](R.id.message_read_receipts)) {
    _.setIcon(ViewWithColor(getColor(R.color.light_graphite)))
  }

  private lazy val senderName   = message.map(_.userId).flatMap(inject[UsersController].displayName)
  private lazy val firstMessage = message.map(_.firstMessage)
  private lazy val msgType      = message.map(_.msgType)

  Signal(senderName, msgType, firstMessage).map {
    case (_,           READ_RECEIPTS_ON,  true)  => getString(R.string.content__system__read_receipts_on)
    case (_,           READ_RECEIPTS_OFF, true)  => getString(R.string.content__system__read_receipts_off)
    case (Me,          READ_RECEIPTS_ON,  false) => getString(R.string.content__system__read_receipts_you_turned_on)
    case (Me,          READ_RECEIPTS_OFF, false) => getString(R.string.content__system__read_receipts_you_turned_off)
    case (Other(name), READ_RECEIPTS_ON,  false) => getString(R.string.content__system__read_receipts_someone_turned_on, name)
    case (Other(name), READ_RECEIPTS_OFF, false) => getString(R.string.content__system__read_receipts_someone_turned_off, name)
    case (name, t, first) =>
     error(l"Unable to create text for name $name and msgType $t, and first message $first")
      ""
  }.onUi { view.setText }
}
