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
package com.waz.zclient.appentry.scenes

import android.content.Context
import android.view._
import android.widget.LinearLayout
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryButtonOnTouchListener
import com.waz.zclient.appentry.controllers.SignInController._
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.utils.LayoutSpec

case class FirstScreenViewHolder(root: View)(implicit val context: Context, eventContext: EventContext, injector: Injector) extends ViewHolder with Injectable {

  val appEntryController = inject[AppEntryController]
  val signInController = inject[SignInController]

  lazy val createTeamButton = root.findViewById[LinearLayout](R.id.create_team_button)
  lazy val createAccountButton = root.findViewById[LinearLayout](R.id.create_account_button)
  lazy val loginButton = root.findViewById[View](R.id.login_button)

  def onCreate(): Unit = {
    createAccountButton.setOnTouchListener(AppEntryButtonOnTouchListener({
      () =>
        appEntryController.goToLoginScreen()
        if (LayoutSpec.isPhone(context))
          signInController.uiSignInState ! SignInMethod(Register, Phone)
        else
          signInController.uiSignInState ! SignInMethod(Register, Email)
    }))
    createTeamButton.setOnTouchListener(AppEntryButtonOnTouchListener(() => appEntryController.createTeam()))
    loginButton.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        appEntryController.goToLoginScreen()
        signInController.uiSignInState ! SignInMethod(Login, Email)
      }
    })
  }
}
