package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.{EditText, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.waz.model.AccountData.Password
import com.waz.utils.returning
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}
import com.wire.signals.EventStream

import scala.util.Try

abstract class ConfirmationWithPasswordDialog extends DialogFragment with FragmentHelper {

  val onAccept = EventStream[Option[Password]]()

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.confirmation_with_password_dialog, null)

  private def providePassword(password: Option[Password]): Unit = {
    onAccept ! password
    dismiss() // if the password is wrong a new dialog will appear
  }

  private lazy val passwordEditText = returning(findById[EditText](root, R.id.confirmation_with_password_edit_text)) { v =>
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

  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.confirmation_with_password_text_input_layout)

  private lazy val forgotPasswordButton = returning(findById[TextView](root, R.id.confirmation_with_password_forgot_password_button)) {
    _.onClick(inject[BrowserController].openForgotPassword())
  }

  def isSSO : Boolean
  def errorMessage: Option[String]
  def title: String
  def message: String
  def positiveButtonText: Int
  def negativeButtonText: Int

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    if(isSSO){
      findById[View](root, R.id.confirmation_with_password_scrollview).setVisible(false)
    }
    passwordEditText.setVisible(!isSSO)
    textInputLayout.setVisible(!isSSO)
    forgotPasswordButton.setVisible(!isSSO)
    errorMessage.foreach(textInputLayout.setError)
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton(positiveButtonText, null)
      .setNegativeButton(negativeButtonText, null)
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
