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

package com.waz.zclient.appentry.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.{FragmentHelper, R}
import com.waz.ZLog.ImplicitTag._

object FirstLaunchAfterLoginFragment {
  val TAG: String = classOf[FirstLaunchAfterLoginFragment].getName

  def newInstance: Fragment = new FirstLaunchAfterLoginFragment

  trait Container {}
}

class FirstLaunchAfterLoginFragment extends BaseFragment[FirstLaunchAfterLoginFragment.Container] with FragmentHelper with View.OnClickListener {

  lazy val appEntryController = inject[AppEntryController]

  private lazy val registerButton = returning(findById[ZetaButton](getView, R.id.zb__first_launch__confirm)){ v =>
    v.setIsFilled(true)
    v.setAccentColor(ContextCompat.getColor(getContext, R.color.text__primary_dark))
  }


  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    registerButton
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_login_first_launch, viewGroup, false)

  override def onResume(): Unit = {
    super.onResume()
    registerButton.setOnClickListener(this)
  }

  override def onPause(): Unit = {
    registerButton.setOnClickListener(null)
    super.onPause()
  }

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.zb__first_launch__confirm =>
        onConfirmClicked()
    }
  }

  private def onConfirmClicked(): Unit = {
    //TODO
//    appEntryController.currentAccount.head.map {
//      case Some(acc) => ZMessaging.currentAccounts.setLoggedIn(acc.id)
//      case _ =>
//    } (Threading.Ui)
  }
}
