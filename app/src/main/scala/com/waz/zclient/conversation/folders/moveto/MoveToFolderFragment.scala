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

import java.util

import android.os.Bundle
import android.view.View
import com.waz.model.ConvId
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.conversationlist.folders.FolderSelectionFragment
import com.waz.zclient.ui.DefaultToolbarFragment

class MoveToFolderFragment extends DefaultToolbarFragment {

  private lazy val convListController = inject[ConversationListController]
  implicit val executionContext = Threading.Ui //TODO: check!!

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    val convId = getArguments.getSerializable(MoveToFolderFragment.KEY_CONV_ID).asInstanceOf[ConvId]

    for {
      customFolders     <- convListController.getCustomFolders
      convFolderId      <- convListController.getCustomFolderId(convId)
      sortedFolders      = customFolders.sortBy(_.name.str)
      currentFolderIndex = convFolderId.fold(-1)(x => sortedFolders.indexWhere(f => f.id == x))
    } yield {
      val folderNames = new util.ArrayList[String]()
      sortedFolders.map(data => folderNames.add(data.name.str))

      openFragmentWithAnimation(
        FolderSelectionFragment.newInstance(folderNames, currentFolderIndex),
        FolderSelectionFragment.TAG
      )
    }
  }

  override protected def onNavigationClick(): Unit = {
    //TODO close the page
  }

  override protected def onActionClick(): Unit = {
    //TODO
  }
}

object MoveToFolderFragment {
  val TAG = classOf[MoveToFolderFragment].getSimpleName

  val KEY_CONV_ID = "convId"

  def newInstance(convId: ConvId) = {
    returning(new MoveToFolderFragment()) { fragment =>
      val bundle = new Bundle()
      bundle.putSerializable(KEY_CONV_ID, convId)
      fragment.setArguments(bundle)
    }
  }
}
