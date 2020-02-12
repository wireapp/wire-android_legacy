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
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.{LayoutInflater, View, WindowManager}
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.utils.events.EventStream
import com.waz.utils.{PasswordValidator, returning}
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.{BuildConfig, FragmentHelper, R}

import scala.util.Try

class BackupPasswordDialog extends DialogFragment with FragmentHelper with DerivedLogTag {
  import BackupPasswordDialog._

  private def mode: DialogMode = getStringArg(MODE_ARG) match {
    case Some(str) if str == InputPasswordMode.str => InputPasswordMode
    case _ => SetPasswordMode
  }

  val onPasswordEntered = EventStream[Password]()

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.backup_password_dialog, null)
  private lazy val keyboard = inject[KeyboardController]

  val minPasswordLength = BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH
  private lazy val strongPasswordValidator =
    PasswordValidator.createStrongPasswordValidator(BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH, BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH)

  private def providePassword(password: String): Unit = {
    onPasswordEntered ! Password(password)
    keyboard.hideKeyboardIfVisible()
    dismiss()
  }

  private lazy val passwordEditText = findById[EditText](root, R.id.backup_password_field)

  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.backup_password_title)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val (title, message) = mode match {
      case SetPasswordMode   => (R.string.backup_password_dialog_title, R.string.backup_password_dialog_message)
      case InputPasswordMode => (R.string.restore_password_dialog_title, R.string.empty_string)
    }

    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(title))
      .setMessage(message)
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create
  }

  override def onStart() = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { d =>
      d.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = {
          val pass = passwordEditText.getText.toString
          mode match {
            case SetPasswordMode if !BuildConfig.FORCE_APP_LOCK && !pass.isEmpty  => providePassword(pass)
            case SetPasswordMode if strongPasswordValidator.isValidPassword(pass) => providePassword(pass)
            case InputPasswordMode                                                => providePassword(pass)
            case _ =>
              textInputLayout.setError(getString(R.string.password_policy_hint, minPasswordLength))
          }
        }
      })
    }
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }
}

object BackupPasswordDialog {
  val FragmentTag = RemoveDeviceDialog.getClass.getSimpleName

  val MODE_ARG: String = "mode"

  sealed trait DialogMode { val str: String }
  case object SetPasswordMode extends DialogMode { override val str = "set_password" }
  case object InputPasswordMode extends DialogMode { override val str = "input_password" }

  def newInstance(mode: DialogMode): BackupPasswordDialog = returning(new BackupPasswordDialog){
    _.setArguments(returning(new Bundle()) { _.putString(MODE_ARG, mode.str) })
  }
}
