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
import android.widget.{CompoundButton, ImageView, TextView}
import android.widget.CompoundButton.OnCheckedChangeListener
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIconWithColor, ViewWithColor}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.getStyledColor
import com.waz.zclient.utils.RichView

class CreateConversationSettingsFragment extends Fragment with FragmentHelper {
  private lazy val createConversationController = inject[CreateConversationController]
  private lazy val userAccountsController       = inject[UserAccountsController]
  private lazy val zms                          = inject[Signal[ZMessaging]]

  private lazy val inputBox = view[InputBox](R.id.input_box)

  private lazy val guestsToggle = returning(view[SwitchCompat](R.id.guest_toggle)) { vh =>
    findById[ImageView](R.id.allow_guests_icon).setImageDrawable(GuestIconWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    createConversationController.teamOnly.currentValue.foreach(teamOnly => vh.foreach(_.setChecked(!teamOnly)))
    vh.foreach(_.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = createConversationController.teamOnly ! !isChecked
    }))
  }

  private lazy val readReceiptsToggle  = returning(view[SwitchCompat](R.id.read_receipts_toggle)) { vh =>
    findById[ImageView](R.id.read_receipts_icon).setImageDrawable(ViewWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))
    zms.flatMap(_.propertiesService.readReceiptsEnabled).onUi { readReceiptsEnabled =>
      createConversationController.readReceipts ! readReceiptsEnabled
      vh.foreach(_.setChecked(readReceiptsEnabled))
    }

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

  private lazy val convOptions = returning(view[View](R.id.create_conv_options)) { vh =>
    userAccountsController.isTeam.onUi(vis => vh.foreach(_.setVisible(vis)))
    vh.foreach(_.onClick {
      optionsVisible.mutate(!_)
    })
  }

  private lazy val convOptionsArrow = returning(view[ImageView](R.id.create_conv_options_icon)) { vh =>
    vh.foreach(_.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40)))
    optionsVisible.map(if (_) -1.0f else 1.0f).onUi(turn => vh.foreach(_.setRotation(turn * 90.0f)))
  }

  private lazy val callInfo = returning(view[TextView](R.id.call_info)){ vh =>
    userAccountsController.isTeam.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

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

    guestsToggle
    readReceiptsToggle

    convOptions
    convOptionsArrow
    convOptionsSubtitle
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    optionsVisible.onChanged.onUi { _ =>
      inject[KeyboardController].hideKeyboardIfVisible()
    }
  }
}

object CreateConversationSettingsFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag
}
