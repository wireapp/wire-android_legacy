package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.view.{LayoutInflater, View, WindowManager}
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.utils.{PasswordValidator, returning}
import com.waz.zclient.{BuildConfig, FragmentHelper, R}
import com.waz.zclient.common.controllers.global.{KeyboardController, PasswordController}

import scala.util.Try

class NewPasswordDialog extends DialogFragment with FragmentHelper with DerivedLogTag {
  import NewPasswordDialog._
  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.new_password_dialog, null)
  private lazy val strongPasswordValidator =
    PasswordValidator.createStrongPasswordValidator(BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH, BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH)
  private lazy val passwordEditText = findById[EditText](root, R.id.new_password_field)
  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.new_password_title)

  private lazy val mode = getStringArg(Mode).fold[Mode](SetMode)(getMode)
  private lazy val keyboard = inject[KeyboardController]

  override def onCreateDialog(savedInstanceState: Bundle): Dialog =
    new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(getString(mode.dialogTitleId))
      .setMessage(getString(R.string.new_password_dialog_message))
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
      .create

  override def onStart(): Unit = {
    super.onStart()
    Try(getDialog.asInstanceOf[AlertDialog]).foreach {
      _.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View): Unit = {
          val pass = passwordEditText.getText.toString
          if (strongPasswordValidator.isValidPassword(pass)) {
            inject[PasswordController].setPassword(Password(pass))
            keyboard.hideKeyboardIfVisible()
            dismiss()
          } else {
            textInputLayout.setError(getString(R.string.password_policy_hint, BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH))
          }
        }
      })
    }
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

  def newInstance(mode: Mode): NewPasswordDialog = returning(new NewPasswordDialog){
    _.setArguments(returning(new Bundle()) { bundle =>
      bundle.putString(Mode, mode.id)
    })
  }

  sealed trait Mode {
    val id: String
    val dialogTitleId: Int
  }
  case object SetMode extends Mode {
    override val id: String = "Set"
    override val dialogTitleId: Int = R.string.new_password_dialog_title
  }
  case object ChangeMode extends Mode {
    override val id: String = "Change"
    override val dialogTitleId: Int = R.string.new_password_dialog_title_change
  }

  def getMode(id: String): Mode = id match {
    case SetMode.id    => SetMode
    case ChangeMode.id => ChangeMode
  }
}
