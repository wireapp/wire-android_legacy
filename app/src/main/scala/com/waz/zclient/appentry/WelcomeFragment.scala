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
import com.waz.zclient._
import com.waz.zclient.appentry.fragments.SignInFragment
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Login, SignInMethod}

class WelcomeFragment extends SSOFragment {

  private lazy val welcomeLoginButton = view[View](R.id.welcomeLoginButton)
  private lazy val welcomeCreateAccountButton = view[View](R.id.welcomeCreateAccountButton)
  private lazy val welcomeEnterpriseLoginButton = view[View](R.id.welcomeEnterpriseLoginButton)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_welcome, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    initCreateAccountButtonListener()
    initLoginButtonListener()
    initEnterpriseLoginButton()
  }

  private def initCreateAccountButtonListener() =
    welcomeCreateAccountButton.foreach(_.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) = startCreateAccountFlow()
    }))

  private def initLoginButtonListener() =
    welcomeLoginButton.foreach(_.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) = startLoginFlow()
    }))

  private def initEnterpriseLoginButton() = {
    welcomeEnterpriseLoginButton.foreach(_.setVisibility(if (BuildConfig.ALLOW_SSO) View.VISIBLE else View.INVISIBLE))
    welcomeEnterpriseLoginButton.foreach(_.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) = startEnterpriseLoginFlow()
    }))
  }

  private def startCreateAccountFlow() =
    activity.showFragment(CreateAccountFragment(), CreateAccountFragment.Tag)

  private def startLoginFlow() =
    activity.showFragment(SignInFragment(SignInMethod(Login, Email)), SignInFragment.Tag)

  private def startEnterpriseLoginFlow() = extractTokenAndShowSSODialog(showIfNoToken = true)


}

object WelcomeFragment {
  val Tag: String =  "WelcomeFragment"

  def apply() = new WelcomeFragment
}
