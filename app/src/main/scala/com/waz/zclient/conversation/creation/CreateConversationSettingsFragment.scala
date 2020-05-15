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
import androidx.fragment.app.Fragment
import android.text.InputFilter.LengthFilter
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, ImageView, TextView}
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.appcompat.widget.SwitchCompat
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIconWithColor, ViewWithColor}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.getStyledColor
import com.waz.zclient.utils.RichView

class CreateConversationSettingsFragment extends Fragment with FragmentHelper {
  private lazy val createConversationController = inject[CreateConversationController]
  private lazy val userAccountsController       = inject[UserAccountsController]

  private lazy val inputBox = view[InputBox](R.id.input_box)
  private lazy val guestsToggle = view[SwitchCompat](R.id.guest_toggle)
  private lazy val convOptions = view[View](R.id.create_conv_options)
  private lazy val convOptionsArrow = view[ImageView](R.id.create_conv_options_icon)
  private lazy val callInfo = returning(view[TextView](R.id.call_info)) { vh =>
    vh.foreach(_.setText(getString(R.string.call_info_text, ConversationController.MaxParticipants.toString)))
  }

  private lazy val readReceiptsToggle  = returning(view[SwitchCompat](R.id.read_receipts_toggle)) { vh =>
    findById[ImageView](R.id.read_receipts_icon).setImageDrawable(ViewWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    vh.foreach(_.setChecked(true))

    vh.foreach(_.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, readReceiptsEnabled: Boolean): Unit =
        createConversationController.readReceipts ! readReceiptsEnabled
    }))
  }

  private lazy val convOptionsSubtitle = returning(view[TypefaceTextView](R.id.create_conv_options_subtitle)) { vh =>
    def onOffStr(flag: Boolean) =
      if (flag) getString(R.string.create_conv_options_subtitle_on)
      else getString(R.string.create_conv_options_subtitle_off)

    Signal(createConversationController.teamOnly, createConversationController.readReceipts).onUi {
      case (teamOnly, readReceipts) =>
        vh.foreach(
          _.setText(s"${getString(R.string.create_conv_options_subtitle_allow_guests)}: ${onOffStr(!teamOnly)}, ${getString(R.string.create_conv_options_subtitle_read_receipts)}: ${onOffStr(readReceipts)}")
        )
    }

  }

  private val optionsVisible = Signal(false)


  private lazy val guestsToggleRow = returning(view[View](R.id.guest_toggle_row)) { vh =>
    Signal(optionsVisible, userAccountsController.isTeam).onUi { case (opt, vis) => vh.foreach(_.setVisible(opt && vis)) }
  }

  private lazy val guestsToggleDesc = returning(view[View](R.id.guest_toggle_description)) { vh =>
    Signal(optionsVisible, userAccountsController.isTeam).onUi { case (opt, vis) => vh.foreach(_.setVisible(opt && vis)) }
  }

  private lazy val readReceiptsToggleRow = returning(view[View](R.id.read_receipts_toggle_row)) { vh =>
    Signal(optionsVisible, userAccountsController.isTeam).onUi { case (opt, vis) => vh.foreach(_.setVisible(opt && vis)) }
  }

  private lazy val readReceiptsToggleDesc = returning(view[View](R.id.read_receipts_toggle_description)) { vh =>
    Signal(optionsVisible, userAccountsController.isTeam).onUi { case (opt, vis) => vh.foreach(_.setVisible(opt && vis)) }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.create_conv_settings_fragment, container, false)

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {

    callInfo
    guestsToggleRow
    guestsToggleDesc
    readReceiptsToggleRow
    readReceiptsToggleDesc

    inputBox.foreach { box =>
      box.text.onUi(createConversationController.name ! _)
      box.editText.setFilters(Array(new LengthFilter(64)))
      box.setValidator(GroupNameValidator)
      createConversationController.name.currentValue.foreach(text => box.editText.setText(text))
      box.errorLayout.setVisible(false)
    }

    guestsToggle.foreach { view =>
      findById[ImageView](R.id.allow_guests_icon).setImageDrawable(GuestIconWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
      createConversationController.teamOnly.currentValue.foreach(teamOnly => view.setChecked(!teamOnly))
      view.setOnCheckedChangeListener(new OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = createConversationController.teamOnly ! !isChecked
      })
    }
    readReceiptsToggle

    convOptions.foreach { view =>
      view.onClick {
        optionsVisible.mutate(!_)
      }
    }

    convOptionsArrow.foreach { view =>
      view.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    }

    convOptionsSubtitle
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    optionsVisible.onChanged.onUi { _ =>
      inject[KeyboardController].hideKeyboardIfVisible()
    }
    userAccountsController.isTeam.onUi(vis => convOptions.foreach(_.setVisible(vis)))
    optionsVisible.map(if (_) -1.0f else 1.0f).onUi(turn => convOptionsArrow.foreach(_.setRotation(turn * 90.0f)))
    userAccountsController.isTeam.onUi(vis => callInfo.foreach(_.setVisible(vis)))
  }
}

object CreateConversationSettingsFragment {
  val Tag: String = getClass.getSimpleName
}
