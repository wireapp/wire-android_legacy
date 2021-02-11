package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface.{BUTTON_NEGATIVE, BUTTON_POSITIVE, OnClickListener}
import android.os.Bundle
import android.text.method.{HideReturnsTransformationMethod, PasswordTransformationMethod}
import android.view.{LayoutInflater, View, WindowManager}
import android.widget.{CheckBox, CompoundButton, EditText, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.model.AccountData.Password
import com.waz.utils.{PasswordValidator, returning}
import com.waz.zclient.{BaseActivity, BuildConfig, FragmentHelper, R}
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.utils.ContextUtils
import ContextUtils._
import android.content.DialogInterface
import com.waz.zclient.ui.utils.KeyboardUtils
import com.wire.signals.EventStream

class NewPasswordDialog extends DialogFragment with FragmentHelper {
  import NewPasswordDialog._

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.new_password_dialog, null)
  private lazy val strongPasswordValidator =
    PasswordValidator.createStrongPasswordValidator(BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH, BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH)
  private lazy val passwordEditText = findById[EditText](root, R.id.new_password_field)
  private lazy val newPasswordHint = returning(findById[TextView](root, R.id.new_password_hint)) { hint =>
    hint.setText(getString(R.string.password_policy_hint, BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH))
  }

  private lazy val showHideButton = returning(findById[CheckBox](root, R.id.new_password_showhide)) { checkbox =>
    checkbox.setButtonDrawable(getDrawable(if (isDarkTheme) R.drawable.visibility_white else R.drawable.visibility_black))

    checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (isChecked)
          passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance)
        else
          passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance)
        passwordEditText.setSelection(passwordEditText.getText.length)
      }
    })
  }

  private lazy val mode = getStringArg(Mode).fold[Mode](SetMode)(getMode)
  private lazy val isDarkTheme = getBooleanArg(IsDarkTheme)
  private lazy val keyboard = inject[KeyboardController]

  private lazy val dialog = {
    val builder = new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(mode.dialogTitleId))
      .setMessage(getString(R.string.new_password_dialog_message))
      .setPositiveButton(mode.dialogButtonId, new OnClickListener() {
        override def onClick(dialog: DialogInterface, which: Int): Unit = checkAndSetPassword()
      })

    if (mode.cancellable) builder.setNegativeButton(android.R.string.cancel, new OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = onAnswer ! None
    })

    builder.create()
  }

  val onAnswer = EventStream[Option[Password]]()

  def close(): Unit = {
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    dismiss()
  }

  private def checkAndSetPassword(): Unit = {
    val pass = passwordEditText.getText.toString
    if (strongPasswordValidator.isValidPassword(pass)) {
      onAnswer ! Some(Password(pass))
    } else {
      newPasswordHint.setTextColor(getColor(R.color.accent_red))
    }
  }

  def show(activity: BaseActivity): Unit =
    activity.getSupportFragmentManager
      .beginTransaction
      .add(this, NewPasswordDialog.Tag)
      .addToBackStack(NewPasswordDialog.Tag)
      .commit

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    dialog
  }

  override def onBackPressed(): Boolean = {
    if (mode.cancellable) onAnswer ! None
    true
  }

  override def onStart(): Unit = {
    super.onStart()
    dialog.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = checkAndSetPassword()
    })

    if (mode.cancellable) dialog.getButton(BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = onAnswer ! None
    })

    newPasswordHint
    showHideButton
    passwordEditText.requestFocus()
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }

  override def onStop(): Unit = {
    keyboard.hideKeyboardIfVisible()
    super.onStop()
  }
}

object NewPasswordDialog {
  val Tag: String = "NewPasswordDialog"

  val Mode: String = "Mode"
  val IsDarkTheme: String = "IsDarkTheme"

  def newInstance(mode: Mode, isDarkTheme: Boolean): NewPasswordDialog = returning(new NewPasswordDialog){ dialog =>
    dialog.setArguments(returning(new Bundle()) { bundle =>
      bundle.putString(Mode, mode.id)
      bundle.putBoolean(IsDarkTheme, isDarkTheme)
    })
    dialog.setCancelable(mode.cancellable)
  }

  sealed trait Mode {
    val id: String
    val dialogTitleId: Int
    val dialogButtonId: Int
    val cancellable: Boolean
  }

  case object SetMode extends Mode {
    override val id: String = "Set"
    override val dialogTitleId: Int = R.string.new_password_dialog_title
    override val dialogButtonId: Int = R.string.new_password_dialog_button
    override val cancellable: Boolean = false
  }

  case object ChangeMode extends Mode {
    override val id: String = "Change"
    override val dialogTitleId: Int = R.string.new_password_dialog_title_change
    override val dialogButtonId: Int = R.string.new_password_dialog_button_change
    override val cancellable: Boolean = true
  }

  case object ChangeInWireMode extends Mode {
    override val id: String = "ChangeInWire"
    override val dialogTitleId: Int = R.string.new_password_dialog_title_change_in_wire
    override val dialogButtonId: Int = R.string.new_password_dialog_button
    override val cancellable: Boolean = false
  }

  def getMode(id: String): Mode = id match {
    case SetMode.id          => SetMode
    case ChangeMode.id       => ChangeMode
    case ChangeInWireMode.id => ChangeInWireMode
  }
}
