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

import android.app.Activity
import android.content.{Context, Intent}
import android.os.Bundle
import android.util.Log
import com.waz.model.ConvId
import com.waz.threading.Threading
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.{BaseActivity, R}

import scala.concurrent.Future

class MoveToFolderActivity extends BaseActivity
  with MoveToFolderFragment.Container
  with CreateNewFolderFragment.Container {

  import Threading.Implicits.Ui

  private lazy val conversationController = inject[ConversationController]
  private lazy val convListController = inject[ConversationListController]

  private lazy val convId = getIntent.getSerializableExtra(MoveToFolderActivity.KEY_CONV_ID).asInstanceOf[ConvId]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_blank)
    getSupportFragmentManager
      .beginTransaction()
      .replace(R.id.activity_blank_framelayout_container, MoveToFolderFragment.newInstance(convId))
      .commit()
  }

  override def onCloseScreenClicked(): Unit = {
    cancelOperation()
  }

  override def onPrepareNewFolderClicked(): Unit = {
    conversationController.getConversation(convId).foreach {
      case Some(conv) => openCreteNewFolderScreen(conv.displayName.toString)
      case None => cancelOperation()
    }
  }

  override def onConvFolderChanged(): Unit = {
    finishOperation()
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

  override def onBackNavigationClicked(): Unit = {
    getSupportFragmentManager.popBackStackImmediate()
  }

  override def onCreateFolderClicked(folderName: String): Unit = {
    (for {
      _ <- convListController.createNewFolderWithConversation(folderName, convId)
    } yield {
      finishOperation()
    }).recoverWith {
      case ex: Exception => Log.e("MoveToFolderActivity",
        "An error occured while creating folder " + folderName + " with conversation " + convId, ex)
        cancelOperation()
        Future.successful(())
    }
  }

  private def finishOperation(): Unit = {
    val resultIntent = new Intent().putExtra(MoveToFolderActivity.KEY_CONV_ID, convId)
    setResult(Activity.RESULT_OK, resultIntent)
    finish()
  }

  private def cancelOperation(): Unit = {
    setResult(Activity.RESULT_CANCELED)
    finish()
  }
}

object MoveToFolderActivity {
  val REQUEST_CODE_MOVE_CREATE = 147

  val KEY_CONV_ID = "convId"

  def newIntent(context: Context, convId: ConvId): Intent = {
    new Intent(context, classOf[MoveToFolderActivity]).putExtra(KEY_CONV_ID, convId)
  }
}
