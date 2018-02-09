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
package com.waz.zclient.conversation.creation

import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.InputFilter.LengthFilter
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator

class NewConversationSettingsFragment extends Fragment with FragmentHelper {
  private lazy val inputBox = view[InputBox](R.id.input_box)

  private lazy val convController = inject[NewConversationController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.create_conv_settings_fragment, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {
    inputBox.foreach(_.setValidator(GroupNameValidator))
    convController.name.currentValue.foreach { text =>
      inputBox.foreach(_.editText.setText(text))
    }

    inputBox.foreach { box =>
      box.text.onUi(convController.name ! _)
      box.editText.setFilters(Array(new LengthFilter(64)))
    }
  }

}

object NewConversationSettingsFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag
}
