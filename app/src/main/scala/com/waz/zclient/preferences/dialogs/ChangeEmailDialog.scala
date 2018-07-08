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
package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.AccountData.Password
import com.waz.model.EmailAddress
import com.waz.service.{UserService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, PasswordValidator}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}
import com.waz.sync.client.ErrorOr

import scala.util.Try

class ChangeEmailDialog extends DialogFragment with FragmentHelper {
  import ChangeEmailDialog._
  import Threading.Implicits.Ui

  val onEmailChanged = EventStream[EmailAddress]()

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val usersService = zms.map(_.users)
  lazy val passwordController = inject[PasswordController]

  private lazy val hasPassword = getBooleanArg(HasPasswordArg)

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.preference_dialog_add_email_password, null)

  private lazy val emailInputLayout = findById[TextInputLayout](root, R.id.til__preferences__email)
  private lazy val emailEditText    = returning(findById[EditText](root, R.id.acet__preferences__email)) { v =>
    v.requestFocus
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          emailInputLayout.getEditText.requestFocus
          true
        } else false
    })
  }

  private lazy val passwordInputLayout = findById[TextInputLayout](root, R.id.til__preferences__password)
  private lazy val passwordEditText = returning(findById[EditText](root, R.id.acet__preferences__password)) { v =>
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          handleInput()
          true
        } else false
    })
  }

  private lazy val emailValidator    = EmailValidator.newInstance
  private lazy val passwordValidator = PasswordValidator.instance(getActivity)


  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {

    //lazy init
    emailInputLayout
    emailEditText
    passwordInputLayout
    passwordEditText

    passwordInputLayout.setVisible(!hasPassword)

    val alertDialog = new AlertDialog.Builder(getActivity)
      .setTitle(if (hasPassword) R.string.pref__account_action__dialog__add_email_password__title else R.string.pref__account_action__dialog__change_email__title)
      .setView(root)
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create
    alertDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    alertDialog
  }

  override def onStart() = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { dialog =>
      dialog.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = handleInput()
      })
    }
  }

  private def handleInput(): Unit = {
    val email = Option(emailInputLayout.getEditText.getText.toString.trim).filter(emailValidator.validate).map(EmailAddress)
    val password = Option(passwordInputLayout.getEditText.getText.toString.trim).filter(passwordValidator.validate).map(Password)

    def setEmail(email: EmailAddress, f: UserService => ErrorOr[Unit]) = {
      usersService.head.flatMap(f(_)).map {
        case Right(_) =>
          onEmailChanged ! email
          dismiss()
        case Left(error) =>
          emailInputLayout.setError(getString(EmailError(error).headerResource))
      }
    }

    (email, password) match {
      case (Some(e), Some(pw)) if !hasPassword  => setEmail(e, _.setEmail(e, pw))
      case (Some(e), _)        if hasPassword => setEmail(e, _.updateEmail(e))
      case _ =>
    }

    if (email.isEmpty) emailInputLayout.setError(getString(R.string.pref__account_action__dialog__add_email_password__error__invalid_email))
    if (password.isEmpty && hasPassword) passwordEditText.setError(getString(R.string.pref__account_action__dialog__add_email_password__error__invalid_password))
  }

}

object ChangeEmailDialog {

  val HasPasswordArg = "ARG_ADDING_NEW_EMAIL"

  val FragmentTag = ChangeEmailDialog.getClass.getSimpleName

  def apply(hasPassword: Boolean) =
    returning(new ChangeEmailDialog()) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putBoolean(HasPasswordArg, hasPassword)
      })
    }
}
