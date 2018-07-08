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
package com.waz.zclient.appentry

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.LinearLayout
import com.waz.ZLog
import com.waz.zclient.appentry.fragments.{SignInFragment, TeamNameFragment}
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.utils.{LayoutSpec, RichView}

object AppLaunchFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag

  def apply(): AppLaunchFragment = new AppLaunchFragment()
}

class AppLaunchFragment extends FragmentHelper {

  private def activity = getActivity.asInstanceOf[AppEntryActivity]

  lazy val createTeamButton = view[LinearLayout](R.id.create_team_button)
  lazy val createAccountButton = view[LinearLayout](R.id.create_account_button)
  lazy val loginButton = view[View](R.id.login_button)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.app_entry_scene, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    createAccountButton.foreach(_.setOnTouchListener(AppEntryButtonOnTouchListener({ () =>
      val inputMethod = if (LayoutSpec.isPhone(getContext)) Phone else Email
      activity.showFragment(SignInFragment(SignInMethod(Register, inputMethod)), SignInFragment.Tag)
    })))
    createTeamButton.foreach(_.setOnTouchListener(AppEntryButtonOnTouchListener({ () =>
      activity.showFragment(TeamNameFragment(), TeamNameFragment.Tag)
    })))
    loginButton.foreach(_.onClick(activity.showFragment(SignInFragment(SignInMethod(Login, Email)), SignInFragment.Tag)))
  }
}
