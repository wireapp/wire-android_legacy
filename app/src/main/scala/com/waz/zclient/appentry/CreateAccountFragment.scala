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
import com.waz.utils.returning
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.appentry.fragments.{SignInFragment, TeamNameFragment}
import com.waz.zclient.utils.LayoutSpec
import com.waz.zclient.{FragmentHelper, R}


class CreateAccountFragment extends FragmentHelper {

  private lazy val createAccountButton = returning(view[View](R.id.create_account_button)) { view =>
    view.onClick { _ =>
      val inputMethod = if (LayoutSpec.isPhone(getContext)) Phone else Email
      parentActivity.showFragment(SignInFragment(SignInMethod(Register, inputMethod)), SignInFragment.Tag)
    }
  }

  private lazy val createTeamButton = returning(view[View](R.id.create_team_button)) { view =>
    view.onClick { _ =>
      parentActivity.showFragment(TeamNameFragment(), TeamNameFragment.Tag)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_create_account_legacy, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    initViews()
  }

  private def initViews() = {
    createAccountButton
    createTeamButton
  }

  override def onBackPressed(): Boolean =
    if (getFragmentManager.getBackStackEntryCount > 1) {
      getFragmentManager.popBackStack()
      true
    } else false


  def parentActivity() = getActivity.asInstanceOf[AppEntryActivity]
}

object CreateAccountFragment {
  val Tag: String = "CreateAccountFragment"

  def apply() = new CreateAccountFragment
}
