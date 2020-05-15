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
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import com.waz.api.EmailCredentials
import com.waz.model.AccountData.Password
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}

import scala.util.Try

class RemoveDeviceDialog extends DialogFragment with FragmentHelper {
  import RemoveDeviceDialog._
  import Threading.Implicits.Ui

  val onDelete = EventStream[Option[Password]]()

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.remove_otr_device_dialog, null)

  private def providePassword(password: Option[Password]): Unit = {
    onDelete ! password
    password.foreach { pwd =>
      for {
        zms         <- inject[Signal[ZMessaging]].head
        Some(am)    <- zms.accounts.activeAccountManager.head
        self        <- am.getSelf
        Some(email) = self.email
        _           <- zms.auth.onPasswordReset(Option(EmailCredentials(email, pwd)))
      } yield ()
    }
    dismiss()
  }

  private lazy val passwordEditText = returning(findById[EditText](root, R.id.acet__remove_otr__password)) { v =>
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

  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.til__remove_otr_device)

  private lazy val forgotPasswordButton = returning(findById[TextView](root, R.id.device_forgot_password)) {
    _.onClick(inject[BrowserController].openForgotPassword())
  }

  private lazy val isSSO = getArguments.getBoolean(IsSSOARG)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    if(isSSO){
      findById[View](root, R.id.remove_otr_device_scrollview).setVisible(false)
    }
    passwordEditText.setVisible(!isSSO)
    textInputLayout.setVisible(!isSSO)
    forgotPasswordButton.setVisible(!isSSO)
    Option(getArguments.getString(ErrorArg)).foreach(textInputLayout.setError)
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(R.string.otr__remove_device__title, getArguments.getString(NameArg, getString(R.string.otr__remove_device__default))))
      .setMessage(if (isSSO) R.string.otr__remove_device__are_you_sure else R.string.otr__remove_device__message)
      .setPositiveButton(R.string.otr__remove_device__button_delete, null)
      .setNegativeButton(R.string.otr__remove_device__button_cancel, null)
      .create
  }

  override def onStart() = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { d =>
      d.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) =
          providePassword(if (isSSO) None else Some(Password(passwordEditText.getText.toString)))
      })
    }
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }
}

object RemoveDeviceDialog {
  val FragmentTag = RemoveDeviceDialog.getClass.getSimpleName
  private val NameArg  = "ARG_NAME"
  private val ErrorArg = "ARG_ERROR"
  private val IsSSOARG = "ARG_IS_SSO"

  def newInstance(deviceName: String, error: Option[String], isSSO: Boolean): RemoveDeviceDialog =
    returning(new RemoveDeviceDialog) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putString(NameArg, deviceName)
        error.foreach(b.putString(ErrorArg, _))
        b.putBoolean(IsSSOARG, isSSO)
      })
    }

}
