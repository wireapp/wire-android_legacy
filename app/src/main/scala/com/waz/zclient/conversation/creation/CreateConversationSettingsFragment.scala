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
import android.support.v7.widget.SwitchCompat
import android.text.InputFilter.LengthFilter
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, TextView}
import android.widget.CompoundButton.OnCheckedChangeListener
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.utils.RichView

class CreateConversationSettingsFragment extends Fragment with FragmentHelper {
  private lazy val convController = inject[CreateConversationController]
  private lazy val userAccountsController = inject[UserAccountsController]

  private lazy val inputBox = view[InputBox](R.id.input_box)

  private lazy val guestsToggle    = view[SwitchCompat](R.id.guest_toggle)

  private lazy val callInfo = returning(view[TextView](R.id.call_info)){ vh =>
    userAccountsController.isTeam.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  private lazy val guestsToggleRow = returning(view[View](R.id.guest_toggle_row)) { vh =>
    userAccountsController.isTeam.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  private lazy val guestsToggleDesc = returning(view[View](R.id.guest_toggle_description)) { vh =>
    userAccountsController.isTeam.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.create_conv_settings_fragment, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {

    callInfo
    guestsToggleRow
    guestsToggleDesc

    inputBox.foreach { box =>
      box.text.onUi(convController.name ! _)
      box.editText.setFilters(Array(new LengthFilter(64)))
      box.setValidator(GroupNameValidator)
      convController.name.currentValue.foreach(text => box.editText.setText(text))
      box.errorLayout.setVisible(false)
    }

    guestsToggle.foreach { toggle =>
      convController.teamOnly.currentValue.foreach(teamOnly => toggle.setChecked(!teamOnly))
      toggle.setOnCheckedChangeListener(new OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = convController.teamOnly ! !isChecked
      })
    }
  }

}

object CreateConversationSettingsFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag
}
