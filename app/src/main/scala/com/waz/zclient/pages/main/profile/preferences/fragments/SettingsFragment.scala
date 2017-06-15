/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.profile.preferences.fragments

import com.waz.zclient.pages.BaseFragment
import SettingsFragment._
import android.app.FragmentTransaction
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog
import com.waz.threading.Threading
import com.waz.zclient.pages.main.profile.preferences.dialogs.AvsOptionsDialogFragment
import com.waz.zclient.pages.main.profile.preferences.views.TextButton
import com.waz.zclient.{BaseActivity, FragmentHelper, R}

class SettingsFragment extends BaseFragment[Container] with FragmentHelper {

  lazy val accountButton = findById[TextButton](R.id.settings_account)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    inflater.inflate(R.layout.preferences_settings_layout, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    accountButton.onClickEvent.on(Threading.Ui) { _ =>
      PreferencesViewsManager(getActivity.asInstanceOf[BaseActivity]).openView(AccountFragment.Tag)
    }
  }
}

object SettingsFragment {
  val Tag = ZLog.logTagFor[SettingsFragment]
  trait Container
}
