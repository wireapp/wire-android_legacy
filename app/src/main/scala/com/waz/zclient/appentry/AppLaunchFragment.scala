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
import android.view.View.OnTouchListener
import android.view.{LayoutInflater, MotionEvent, View, ViewGroup}
import android.widget.{ImageView, LinearLayout}
import com.waz.zclient.R
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.appentry.fragments.{SignInFragment, TeamNameFragment}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.showInfoDialog
import com.waz.zclient.utils.{BackendController, LayoutSpec, RichView}

object AppLaunchFragment {

  val Tag: String = getClass.getSimpleName

  def apply(): AppLaunchFragment = new AppLaunchFragment()
}

class AppLaunchFragment extends SSOFragment {

  protected def activity = getActivity.asInstanceOf[AppEntryActivity]

  private lazy val logo = view[ImageView](R.id.app_entry_logo_image)

  private lazy val backendInfo = view[LinearLayout](R.id.app_entry_custom_backend_container)
  private lazy val backendTitle = view[TypefaceTextView](R.id.app_entry_custom_backend_title)
  private lazy val backendSubtitle = view[TypefaceTextView](R.id.app_entry_custom_backend_subtitle)
  private lazy val backendShowMoreButton = view[TypefaceTextView](R.id.customBE_show_more_button)

  private lazy val createTeamButton = view[LinearLayout](R.id.create_team_button)
  private lazy val createAccountButton = view[LinearLayout](R.id.create_account_button)

  private lazy val loginButton = view[View](R.id.login_button)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.app_entry_scene, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val backendController = inject[BackendController]
    val hasCustomBackend = backendController.hasCustomBackend

    if (hasCustomBackend) {
      logo.foreach(_.setVisible(false))
      backendInfo.foreach(_.setVisible(true))

      val name = backendController.getStoredBackendConfig.map(_.environment).getOrElse("N/A")
      backendTitle.foreach(_.setText(getString(R.string.custom_backend_info_title, name)))

      val configUrl = backendController.customBackendConfigUrl.getOrElse("N/A").toUpperCase
      backendSubtitle.foreach(_.setText(configUrl))

      backendShowMoreButton.foreach(_.setOnTouchListener(new OnTouchListener {
        override def onTouch(v: View, event: MotionEvent): Boolean = {
          showInfoDialog(getString(R.string.custom_backend_dialog_info_title, name), configUrl)
          false
        }
      }))
    }

    createAccountButton.foreach { v =>
      v.setVisible(!hasCustomBackend)
      v.setOnTouchListener(AppEntryButtonOnTouchListener({ () =>
        val inputMethod = if (LayoutSpec.isPhone(getContext)) Phone else Email
        activity.showFragment(SignInFragment(SignInMethod(Register, inputMethod)), SignInFragment.Tag)
      }))
    }

    createTeamButton.foreach { v =>
      v.setVisible(!hasCustomBackend)
      v.setOnTouchListener(AppEntryButtonOnTouchListener({ () =>
        activity.showFragment(TeamNameFragment(), TeamNameFragment.Tag)
      }))
    }

    loginButton.foreach(_.onClick(activity.showFragment(SignInFragment(SignInMethod(Login, Email)), SignInFragment.Tag)))
  }

}
