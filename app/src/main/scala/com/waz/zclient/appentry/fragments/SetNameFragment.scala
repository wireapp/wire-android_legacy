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
import androidx.fragment.app.FragmentManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import com.waz.zclient._
import com.waz.zclient.appentry.CreateTeamFragment
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.NameValidator
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._

import scala.concurrent.Future

case class SetNameFragment() extends CreateTeamFragment {

  override val layoutId: Int = R.layout.set_name_scene

  lazy val inputField = view[InputBox](R.id.input_field)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = inputField.foreach { inputField =>
    inputField.setValidator(NameValidator)
    inputField.editText.requestFocus()
    inputField.editText.setText(createTeamController.teamUserName)
    inputField.editText.addTextListener(createTeamController.teamUserName = _)
    inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
    KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
    inputField.setOnClick(text => Future.successful {
      createTeamController.teamUserName = text
      showFragment(SetTeamPasswordFragment(), SetTeamPasswordFragment.Tag)
      None
    })
  }

  override def onBackPressed(): Boolean = {
    createTeamController.code = ""
    getFragmentManager.popBackStack(VerifyTeamEmailFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    true
  }
}

object SetNameFragment {
  val Tag: String = getClass.getSimpleName
}
