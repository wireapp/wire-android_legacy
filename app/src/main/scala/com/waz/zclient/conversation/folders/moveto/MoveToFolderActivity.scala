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
package com.waz.zclient.conversation.folders.moveto

import android.content.{Context, Intent}
import android.os.Bundle
import com.waz.model.ConvId
import com.waz.threading.Threading
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{BaseActivity, R}

import scala.concurrent.ExecutionContext

class MoveToFolderActivity extends BaseActivity with MoveToFolderFragment.Container {

  implicit val executionContext: ExecutionContext = Threading.Ui //TODO: check!!

  private lazy val conversationController = inject[ConversationController]

  private lazy val convId = getIntent.getSerializableExtra(MoveToFolderActivity.KEY_CONV_ID).asInstanceOf[ConvId]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_blank)
    getSupportFragmentManager
      .beginTransaction()
      .replace(R.id.activity_blank_framelayout_container, MoveToFolderFragment.newInstance(convId))
      .commit()
  }

  override def onPrepareNewFolderClicked(): Unit = {
    conversationController.getConversation(convId).foreach {
      case Some(conv) => openCreteNewFolderScreen(conv.name.getOrElse("").toString)
      case None => //TODO: conversation is deleted. what to do?
    }
  }

  private def openCreteNewFolderScreen(convName: String): Unit = {
    getSupportFragmentManager
      .beginTransaction()
      .replace(R.id.activity_blank_framelayout_container,
        CreateNewFolderFragment.newInstance(convName),
        CreateNewFolderFragment.TAG)
      .addToBackStack(CreateNewFolderFragment.TAG)
      .commit()
  }
}

object MoveToFolderActivity {
  val KEY_CONV_ID = "convId"

  def newIntent(context: Context, convId: ConvId): Intent = {
    new Intent(context, classOf[MoveToFolderActivity]).putExtra(KEY_CONV_ID, convId)
  }
}
