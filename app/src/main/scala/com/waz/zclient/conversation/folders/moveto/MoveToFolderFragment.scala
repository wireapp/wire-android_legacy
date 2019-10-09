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

import android.os.Bundle
import android.view.View
import com.waz.zclient.conversationlist.folders.FolderSelectionFragment
import com.waz.zclient.ui.DefaultToolbarFragment

class MoveToFolderFragment extends DefaultToolbarFragment {

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    openFragmentWithAnimation(FolderSelectionFragment.newInstance(), FolderSelectionFragment.TAG)
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

  def newInstance = new MoveToFolderFragment()
}
