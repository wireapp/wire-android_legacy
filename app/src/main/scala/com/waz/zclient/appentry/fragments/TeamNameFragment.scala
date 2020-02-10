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

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.waz.zclient._
import com.waz.zclient.appentry.{CreateTeamFragment, SSOFragment}
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.NameValidator
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._

import scala.concurrent.Future

class TeamNameFragment extends CreateTeamFragment with SSOFragment {

  override val layoutId: Int = R.layout.create_team_name_scene

  private lazy val inputField = view[InputBox](R.id.input_field)
  private lazy val about = view[View](R.id.about)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    inputField.foreach { inputField =>
      inputField.setValidator(NameValidator)
      inputField.editText.setText(createTeamController.teamName)
      inputField.editText.requestFocus()
      KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
      inputField.setOnClick( text =>
        Future.successful {
          createTeamController.teamName = text
          showFragment(SetTeamEmailFragment(), SetTeamEmailFragment.Tag)
          None
        }
      )
    }
    about.foreach(_.onClick(inject[BrowserController].openAboutTeams()))
  }
}

object TeamNameFragment {
  val Tag: String = "TeamNameFragment"

  def apply() = new TeamNameFragment()
}
