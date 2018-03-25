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

import android.app.Activity
import android.content.{Context, Intent}
import android.net.Uri
import android.text.InputType
import android.view.View
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.appentry.EntryError
import com.waz.zclient.appentry.controllers.AppEntryController
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Register, SignInMethod}
import com.waz.zclient.appentry.scenes.SetEmailViewHolder._
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.EmailValidator
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils._

case class SetEmailViewHolder(root: View)(implicit val context: Context, eventContext: EventContext, injector: Injector) extends ViewHolder with Injectable {

  private val appEntryController = inject[AppEntryController]

  lazy val inputField = root.findViewById[InputBox](R.id.input_field)
  lazy val aboutButton = root.findViewById[TypefaceTextView](R.id.about_button)

  def onCreate(): Unit = {
    inputField.setValidator(EmailValidator)
    inputField.editText.setText(appEntryController.teamEmail)
    inputField.editText.addTextListener { text =>
      appEntryController.teamEmail = text
      aboutButton.setVisibility(View.INVISIBLE)
    }
    inputField.editText.requestFocus()
    inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])
    inputField.setOnClick( text => appEntryController.requestTeamEmailVerificationCode(text).map {
      case Right(error) =>
        val errorMessage = getString(EntryError(error.code, error.label, SignInMethod(Register, Email)).bodyResource)
        if (error.code == DuplicateEmailErrorCode) {
          aboutButton.setVisible(true)
        }
        Some(errorMessage)
      case _ => None
    } (Threading.Ui))

    aboutButton.onClick(openUrl(R.string.teams_set_email_about_url))
  }

  private def openUrl(id: Int): Unit ={
    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(id))))
  }
}

object SetEmailViewHolder {
  val DuplicateEmailErrorCode = 409
}
