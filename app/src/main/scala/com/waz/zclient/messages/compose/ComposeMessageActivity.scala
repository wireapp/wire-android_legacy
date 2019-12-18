/**
  * Wire
  * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.messages.compose

import android.content.Intent
import android.os.Bundle
import android.view.{MotionEvent, View}
import android.widget.Toast
import com.waz.model.MessageData
import com.waz.zclient._
import com.waz.zclient.controllers.globallayout.{IGlobalLayoutController, KeyboardVisibilityObserver}
import com.waz.zclient.cursor.{CursorCallback, CursorController, CursorView}
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor.CursorMenuItem

class ComposeMessageActivity extends BaseActivity with ActivityHelper {

  lazy val messageEditText: CursorView = findById[CursorView](R.id.compose_message_et)

  private lazy val extendedCursorContainer = findById[ExtendedCursorContainer](R.id.ecc__conversation)
  private lazy val globalLayoutController  = inject[IGlobalLayoutController]

  private val keyboardVisibilityObserver = new KeyboardVisibilityObserver {
    override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit =
      inject[CursorController].notifyKeyboardVisibilityChanged(keyboardIsVisible)
  }

  private val cursorCallback = new CursorCallback {
    override def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit = ???

    override def hideExtendedCursor(): Unit = extendedCursorContainer.foreach {
      case ecc if ecc.isExpanded => ecc.close(false)
      case _                     =>
    }

    override def openFileSharing(): Unit = ???

    override def captureVideo(): Unit = ???

    override def onMessageSent(msg: MessageData): Unit = ???

    override def onCursorButtonLongPressed(cursorMenuItem: CursorMenuItem): Unit = ???

    override def onMotionEventFromCursorButton(cursorMenuItem: CursorMenuItem, motionEvent: MotionEvent): Unit = ???

    override def onCursorClicked(): Unit = ???
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_compose_new_message)
    globalLayoutController.addKeyboardVisibilityObserver(keyboardVisibilityObserver)
    messageEditText.sendButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (messageEditText.cursorEditText.length() == 0) {
          Toast.makeText(ComposeMessageActivity.this, "Please enter a message", Toast.LENGTH_LONG).show()
        } else {
          val intent = new Intent(ComposeMessageActivity.this, classOf[ShareActivity])
          intent.setAction(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_TEXT, messageEditText.cursorEditText.getText.toString)
          intent.setType("text/plain")
          startActivity(intent)
        }
      }
    })
  }

  override def onStop() {
    globalLayoutController.removeKeyboardVisibilityObserver(keyboardVisibilityObserver)
  }

}
