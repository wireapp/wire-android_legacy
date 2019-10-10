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
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.zclient.R
import com.waz.zclient.ui.DefaultToolbarFragment

class CreateNewFolderFragment extends DefaultToolbarFragment {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_create_new_folder, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    setTitle(getString(R.string.folders_create_new_folder))
    setActionButtonText(getString(R.string.folders_create_new_folder_action))
  }

  override protected def onNavigationClick(): Unit = {}

  override protected def onActionClick(): Unit = {}

  override protected def getToolbarId = R.id.fragment_create_new_folder_toolbar
}

object CreateNewFolderFragment {
  val TAG = "CreateNewFolderFragment"

  def newInstance() = new CreateNewFolderFragment()
}
