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
import com.waz.model.AccountData.Password
import com.waz.utils.events.EventStream
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}

import scala.util.Try

class BackupPasswordDialog extends DialogFragment with FragmentHelper {

  val onPasswordEntered = EventStream[Option[Password]]()

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.backup_password_dialog, null)

  private def providePassword(password: Option[Password]): Unit = {
    onPasswordEntered ! password
    dismiss()
  }

  private lazy val passwordEditText = returning(findById[EditText](root, R.id.acet__backup_password_field)) { v =>
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent) =
        actionId match {
          case EditorInfo.IME_ACTION_DONE =>
            providePassword(Some(Password(v.getText.toString)))
            true
          case _ => false
        }
    })
  }

  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.til__backup_password)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(R.string.backup_password_dialog_title))
      .setMessage(R.string.backup_password_dialog_message)
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
          providePassword(if (pass.isEmpty) None else Some(Password(pass)))
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
}
