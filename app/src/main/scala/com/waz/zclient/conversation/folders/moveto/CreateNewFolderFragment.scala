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
import androidx.core.content.ContextCompat
import android.util.TypedValue
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.ui.DefaultToolbarFragment
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils

class CreateNewFolderFragment extends DefaultToolbarFragment[CreateNewFolderFragment.Container] {

  private lazy val textViewInfo = view[TextView](R.id.fragment_create_new_folder_textview_info)
  private lazy val inputBox = view[InputBox](R.id.fragment_create_new_folder_inputbox_folder_name)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_create_new_folder, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    setActionButtonEnabled(false)
    setTitle(getString(R.string.folders_create_new_folder))
    toolbar.foreach(t => {
      val typedvalueattr = new TypedValue
      getContext.getTheme.resolveAttribute(R.attr.backNavigationIcon, typedvalueattr, true)
      val resId = typedvalueattr.resourceId
      t.setNavigationIcon(ContextCompat.getDrawable(getContext, resId))
    })
    setActionButtonText(getString(R.string.folders_create_new_folder_action))
    setUpInputBoxValidations()
    textViewInfo.foreach(_.setText(getString(
      R.string.folders_create_new_folder_info,
      getArguments.getString(CreateNewFolderFragment.KEY_CONVERSATION_NAME)
    )))
  }

  private def setUpInputBoxValidations(): Unit = {
    val maxNameLength = ContextUtils.getInt(R.integer.max_folder_name_length)
    inputBox.foreach { box =>

      box.errorText.setGravity(Gravity.START)
      box.errorText.setTextColor(ContextUtils.getColor(R.color.teams_placeholder_text))

      box.setShouldDisableOnClick(false)
      box.setShouldClearErrorOnClick(false)
      box.setShouldClearErrorOnTyping(false)

      box.showErrorMessage(Some(getString(R.string.folders_folder_name_error_text, maxNameLength.toString)))
      val validator = GroupNameValidator
      box.setValidator(validator)

      box.text.onUi(s => {
        val maxLengthExceeded = s.length() > maxNameLength
        box.errorText.setTextColor(ContextUtils.getColor(
          if (maxLengthExceeded) R.color.teams_error_red else R.color.teams_placeholder_text))
        setActionButtonEnabled(validator.isValid(s.toString) && !maxLengthExceeded)
      })
    }
  }

  override protected def onNavigationClick(): Unit = {
    getContainer.onBackNavigationClicked()
  }

  override protected def onActionClick(): Unit = {
    inputBox.foreach(v => {
      setActionButtonEnabled(false)
      KeyboardUtils.hideKeyboard(getActivity)
      getContainer.onCreateFolderClicked(v.editText.getText.toString)
    })
  }

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

  trait Container {
    def onBackNavigationClicked(): Unit
    def onCreateFolderClicked(folderName: String): Unit
  }
}
