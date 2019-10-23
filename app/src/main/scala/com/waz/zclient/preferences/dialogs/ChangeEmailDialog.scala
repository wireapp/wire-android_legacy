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
import com.google.android.material.textfield.TextInputLayout
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.EmailAddress
import com.waz.service.{UserService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.pages.main.profile.validator.EmailValidator
import com.waz.zclient.utils.RichView
import com.waz.zclient.{BuildConfig, FragmentHelper, R}
import com.waz.sync.client.ErrorOr
import com.waz.utils.{returning, PasswordValidator => StrongValidator}
import com.waz.zclient.utils.ContextUtils.getColor

import scala.util.Try

class ChangeEmailDialog extends DialogFragment with FragmentHelper with DerivedLogTag {
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

  private lazy val passwordPolicyHint = findById[TextView](root, R.id.add_email_password_policy_hint)

  private lazy val passwordInputLayout = findById[TextInputLayout](root, R.id.til__preferences__password)
  private lazy val passwordEditText = returning(findById[EditText](root, R.id.acet__preferences__password)) { v =>
    v.setOnKeyListener(new View.OnKeyListener {
      def onKey(v: View, keyCode: Int, event: KeyEvent): Boolean = {
        if (!hasPassword) passwordPolicyHint.setTextColor(getColor(R.color.white))
        false
      }
    })
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          handleInput()
          true
        } else false
    })
  }

  private lazy val emailValidator    = EmailValidator.newInstance

  // This is used when setting a password once the confirmation button is clicked.
  private val minPasswordLength = BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH
  private val maxPasswordLength = BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH

  private lazy val strongPasswordValidator =
    StrongValidator.createStrongPasswordValidator(minPasswordLength, maxPasswordLength)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    //lazy init
    emailInputLayout
    emailEditText
    passwordInputLayout
    passwordEditText

    if (!hasPassword) {
      passwordPolicyHint.setVisible(true)
      passwordPolicyHint.setText(getString(R.string.password_policy_hint, minPasswordLength))
    }

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
    val passwordText = Option(passwordInputLayout.getEditText.getText.toString.trim).getOrElse("")
    val password =
      if (hasPassword) {
        None
      } else if (!TextUtils.isEmpty(passwordText)) {
        if (strongPasswordValidator.isValidPassword(passwordText)) {
          Some(Password(passwordText))
        } else {
          passwordPolicyHint.setTextColor(getColor(R.color.teams_error_red))
          None
        }
      } else {
        passwordEditText.setError(getString(R.string.pref__account_action__dialog__add_email_password__error__invalid_password))
        None
      }

    val email = Option(emailInputLayout.getEditText.getText.toString.trim).filter(emailValidator.validate).map(EmailAddress)
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
