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
import android.view.{LayoutInflater, View, ViewGroup, WindowManager}
import android.widget.Button
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Login, SignInMethod}
import com.waz.zclient.feature.auth.registration.CreateAccountActivity

class WelcomeFragment extends SSOFragment {

  private lazy val welcomeLoginButton = returning(view[Button](R.id.welcomeLoginButton)) { view =>
    view.onClick { _ =>
      startLoginFlow()
    }
  }

  private lazy val welcomeCreateAccountButton = returning(view[Button](R.id.welcomeCreateAccountButton)) { view =>
    view.onClick { _ =>
      startCreateAccountFlow()
    }
  }

  private lazy val welcomeEnterpriseLoginButton = returning(view[Button](R.id.welcomeEnterpriseLoginButton)) { view =>
    view.foreach(_.setVisibility(if (BuildConfig.ALLOW_SSO) View.VISIBLE else View.INVISIBLE))
    view.onClick { _ =>
      startEnterpriseLoginFlow()
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_welcome, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    initViews()
  }

  override def onResume(): Unit = {
    super.onResume()
    activity.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
  }

  override def onPause(): Unit = {
    super.onPause()
    activity.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  }

  private def initViews() = {
    welcomeLoginButton
    welcomeCreateAccountButton
    welcomeEnterpriseLoginButton
  }

  private def startCreateAccountFlow(): Unit =
    if (BuildConfig.KOTLIN_REGISTRATION) {
      startActivity(CreateAccountActivity.newIntent(getActivity))
    } else {
      activity.showFragment(CreateAccountFragment(), CreateAccountFragment.Tag)
    }

  private def startLoginFlow(): Unit =
    activity.showFragment(SignInFragment(SignInMethod(Login, Email)), SignInFragment.Tag)

  private def startEnterpriseLoginFlow(): Unit = extractTokenAndShowSSODialog(showIfNoToken = true)
}

object WelcomeFragment {
  val Tag: String = "WelcomeFragment"

  def apply() = new WelcomeFragment
}
