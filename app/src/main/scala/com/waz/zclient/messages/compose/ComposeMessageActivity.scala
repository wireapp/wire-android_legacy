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

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.ui.cursor.CursorEditText
import com.waz.zclient.{ActivityHelper, BaseActivity, R, ShareActivity}

class ComposeMessageActivity extends BaseActivity with ActivityHelper {

  lazy implicit val context: Context = this

  lazy val sendButton = findById[GlyphButton](R.id.send_button)

  lazy val messageEditText = findById[CursorEditText](R.id.compose_message_et)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_compose_new_message)
    sendButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (messageEditText.length() == 0) {
          Toast.makeText(ComposeMessageActivity.this, "Please enter a message", Toast.LENGTH_LONG).show()
        } else {
          val intent = new Intent(ComposeMessageActivity.this, classOf[ShareActivity])
          intent.setAction(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_STREAM, messageEditText.getText.toString)
          intent.setType("image/jpeg")
          startActivity(intent)
        }
      }
    })
  }

}
