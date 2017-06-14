/**
  * Wire
  * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, WindowManager}
import android.widget.{EditText, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose}
import com.waz.api.impl.EmailCredentials
import com.waz.model.EmailAddress
import com.waz.service.ZMessaging
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}

class AccountSignInPreference extends DialogFragment with FragmentHelper {

  private lazy val layout = LayoutInflater.from(getActivity).inflate(R.layout.preference_dialog_sign_in, null)

  private lazy val emailLayout    = findById[TextInputLayout](layout, R.id.sign_in_email_wrapper)
  private lazy val passwordLayout = findById[TextInputLayout](layout, R.id.sign_in_password_wrapper)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {

    returning(findById[EditText](layout, R.id.sign_in_email))(_.requestFocus)
    returning(findById[EditText](layout, R.id.sign_in_password)) { et =>
      et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
          if (actionId == EditorInfo.IME_ACTION_DONE) {
            handleInput()
            true
          } else
            false
      })
    }

    val alertDialogBuilder = new AlertDialog.Builder(getActivity)
      .setTitle(R.string.pref_dev_category_sign_in_account_title)
      .setView(layout)
      .setPositiveButton(android.R.string.ok, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = handleInput()
      })
      .setNegativeButton(android.R.string.cancel, null)

    returning(alertDialogBuilder.create)(_.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE))
  }

  private def handleInput() = {
    import com.waz.threading.Threading.Implicits.Background

    val email    = EmailAddress(emailLayout.getEditText.getText.toString.trim)
    val password = passwordLayout.getEditText.getText.toString.trim

    verbose(s"Logging into account: $email/$password")

    ZMessaging.currentAccounts.login(EmailCredentials(email, Some(password))).map {
      case Right(data) => verbose("Login successful")
      case Left(err)   => error(s"Login failed with error: $err")
    }
  }
}

object AccountSignInPreference {
  val FragmentTag = "AccountSignInPreference"
}
