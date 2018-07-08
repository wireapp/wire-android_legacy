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
package com.waz.zclient.cursor

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, TextView}
import com.waz.threading.Threading
import com.waz.zclient.ViewHelper
import com.waz.zclient.R
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.ZLog.ImplicitTag._

class EditCursorToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
    extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {

  def this(context: Context, attrs: AttributeSet) { this(context, attrs, 0) }
  def this(context: Context) { this(context, null) }

  import Threading.Implicits.Ui

  val controller = inject[CursorController]

  inflate(R.layout.cursor_edit_toolbar_content)

  val closeButton: View       = findById(R.id.gtv__edit_message__close)
  val approveButton: TextView = findById(R.id.gtv__edit_message__approve)
  val resetButton: TextView   = findById(R.id.gtv__edit_message__reset)

  val enabledTextColor = getStyledColor(R.attr.cursorEditButtons)
  val disabledTextColor = getStyledColor(R.attr.cursorEditButtonsDisabled)

  val messageChanged = controller.editingMsg.zip(controller.enteredText) map {
    case (Some(msg), text) => msg.contentString != text
    case _ => false
  }

  val textColor = messageChanged map {
    case true => enabledTextColor
    case false => disabledTextColor
  }

  messageChanged.on(Threading.Ui) { enabled =>
    resetButton.setClickable(enabled)
    approveButton.setClickable(enabled)
  }

  textColor.on(Threading.Ui) { color =>
    resetButton.setTextColor(color)
    approveButton.setTextColor(color)
  }

  closeButton.onClick { controller.editingMsg ! None }
  resetButton.onClick { controller.onEditMessageReset ! (()) }
  approveButton.onClick {
    controller.enteredText.head foreach { controller.submit }
  }
}
