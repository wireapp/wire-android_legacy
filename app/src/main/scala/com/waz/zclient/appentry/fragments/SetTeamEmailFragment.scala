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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import com.waz.ZLog
import com.waz.model.EmailAddress
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.appentry.CreateTeamFragment
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.fragments.SetTeamEmailFragment._
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.EmailValidator
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._

case class SetTeamEmailFragment() extends CreateTeamFragment {

  override val layoutId: Int = R.layout.set_email_scene

  private lazy val inputField = view[InputBox](R.id.input_field)
  private lazy val aboutButton = view[TypefaceTextView](R.id.about_button)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    inputField.foreach { inputField =>
      inputField.setValidator(EmailValidator)
      inputField.editText.setText(createTeamController.teamEmail)
      inputField.editText.addTextListener { text =>
        createTeamController.teamEmail = text
        aboutButton.foreach(_.setVisibility(View.INVISIBLE))
      }
      inputField.editText.requestFocus()
      inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
      KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
      inputField.setOnClick { text =>
        accountsService.requestEmailCode(EmailAddress(text)).map {
          case Left(err) =>
            if (err.code == DuplicateEmailErrorCode) aboutButton.foreach(_.setVisible(true))
            Some(getString(EmailError(err).bodyResource))
          case _ =>
            showFragment(VerifyTeamEmailFragment(), VerifyTeamEmailFragment.Tag)
            None
        }(Threading.Ui)
      }
    }
    aboutButton.foreach(_.onClick(openUrl(R.string.teams_set_email_about_url)))
  }

  private def openUrl(id: Int): Unit ={
    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(id))))
  }
}

object SetTeamEmailFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag
  val DuplicateEmailErrorCode = 409
}
