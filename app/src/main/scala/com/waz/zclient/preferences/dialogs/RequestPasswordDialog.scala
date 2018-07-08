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

//TODO merge with RemoveDeviceDialog somehow - a lot of common code (for now it's only used in dev preferences)
class RequestPasswordDialog extends DialogFragment with FragmentHelper {
  import RequestPasswordDialog._

  val onPassword = EventStream[Password]()

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.remove_otr_device_dialog, null)

  private lazy val passwordEditText = returning(findById[EditText](root, R.id.acet__remove_otr__password)) { v =>
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent) =
        actionId match {
          case EditorInfo.IME_ACTION_DONE =>
            onPassword ! Password(v.getText.toString)
            dismiss()
            true
          case _ => false
        }
    })
  }
  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.til__remove_otr_device)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {

    passwordEditText
    textInputLayout
    Option(getArguments.getString(ErrorArg)).foreach(textInputLayout.setError)
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(R.string.pref_dev_provide_password))
      .setMessage(R.string.pref_dev_provide_password)
      .setPositiveButton(R.string.otr__remove_device__button_delete, null)
      .setNegativeButton(R.string.otr__remove_device__button_cancel, null)
      .create
  }

  override def onStart() = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { d =>
      d.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = {
          onPassword ! Password(passwordEditText.getText.toString)
          dismiss()
        }
      })
    }
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }
}

object RequestPasswordDialog {
  val Tag = RequestPasswordDialog.getClass.getSimpleName
  private val ErrorArg = "ARG_ERROR"

  def newInstance(error: Option[String]): RequestPasswordDialog =
    returning(new RequestPasswordDialog) {
      _.setArguments(returning(new Bundle()) { b =>
        error.foreach(b.putString(ErrorArg, _))
      })
    }

}


