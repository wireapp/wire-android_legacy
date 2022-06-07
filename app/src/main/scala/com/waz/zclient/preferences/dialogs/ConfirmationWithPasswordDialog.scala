package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, WindowManager}
import android.widget.{EditText, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.waz.model.AccountData.Password
import com.waz.utils.returning
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}
import com.wire.signals.EventStream

abstract class ConfirmationWithPasswordDialog extends DialogFragment with FragmentHelper {

  val onAccept = EventStream[Option[Password]]()
  val onDecline = EventStream[Unit]

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.confirmation_with_password_dialog, null)

  private def providePassword(password: Option[Password]): Unit = {
    KeyboardUtils.hideKeyboard(textInputLayout)
    onAccept ! password
    dismiss() // if the password is wrong a new dialog will appear
  }

  private def onNegativeClick(): Unit = {
    KeyboardUtils.hideKeyboard(textInputLayout)
    onDecline ! (())
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

  private lazy val messageTextView = findById[TypefaceTextView](root, R.id.confirmation_with_password_message_text_view)
  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.confirmation_with_password_text_input_layout)

  private lazy val forgotPasswordButton = returning(findById[TextView](root, R.id.confirmation_with_password_forgot_password_button)) {
    _.onClick(inject[BrowserController].openForgotPassword())
  }

  private lazy val dialogClickListener = new DialogInterface.OnClickListener {
    override def onClick(dialog: DialogInterface, which: Int): Unit = which match {
      case DialogInterface.BUTTON_POSITIVE => providePassword(if (isPasswordManagedByCompany) None else Some(Password(passwordEditText.getText.toString)))
      case DialogInterface.BUTTON_NEGATIVE => onNegativeClick()
      case _ =>
    }
  }

  def isPasswordManagedByCompany: Boolean
  def errorMessage: Option[String]
  def title: String
  def message: String
  def positiveButtonText: Int
  def negativeButtonText: Int

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    passwordEditText.setVisible(!isPasswordManagedByCompany)
    textInputLayout.setVisible(!isPasswordManagedByCompany)
    forgotPasswordButton.setVisible(!isPasswordManagedByCompany)
    messageTextView.setText(message)
    errorMessage.foreach(textInputLayout.setError)
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(title)
      .setPositiveButton(positiveButtonText, dialogClickListener)
      .setNegativeButton(negativeButtonText, dialogClickListener)
      .create
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }
}
