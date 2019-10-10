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
import android.text.InputFilter.LengthFilter
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.ui.DefaultToolbarFragment

class CreateNewFolderFragment extends DefaultToolbarFragment {

  private lazy val textViewInfo = view[TextView](R.id.fragment_create_new_folder_textview_info)
  private lazy val inputBox = view[InputBox](R.id.fragment_create_new_folder_inputbox_folder_name)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_create_new_folder, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    setTitle(getString(R.string.folders_create_new_folder))
    setActionButtonText(getString(R.string.folders_create_new_folder_action))
    inputBox.foreach { box =>
      box.editText.setFilters(Array(new LengthFilter(64)))
      box.setValidator(GroupNameValidator)
    }
    textViewInfo.foreach(_.setText(getString(
      R.string.folders_create_new_folder_info,
      getArguments.getString(CreateNewFolderFragment.KEY_CONVERSATION_NAME)
    )))
  }

  override protected def onNavigationClick(): Unit = {}

  override protected def onActionClick(): Unit = {}

  override protected def getToolbarId = R.id.fragment_create_new_folder_toolbar
}

object CreateNewFolderFragment {
  val TAG = "CreateNewFolderFragment"

  val KEY_CONVERSATION_NAME = "conversationName"

  def newInstance(conversationName: String) =
    returning(new CreateNewFolderFragment()) { fragment =>
      val bundle = new Bundle()
      bundle.putString(KEY_CONVERSATION_NAME, conversationName)
      fragment.setArguments(bundle)
    }
}
