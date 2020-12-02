package com.waz.zclient.preferences.dialogs

import android.app.Dialog
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.text.method.{HideReturnsTransformationMethod, PasswordTransformationMethod}
import android.view.{LayoutInflater, View, WindowManager}
import android.widget.{EditText, ImageView, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.model.AccountData.Password
import com.waz.utils.{PasswordValidator, returning}
import com.waz.zclient.{BuildConfig, FragmentHelper, R}
import com.waz.zclient.common.controllers.global.{KeyboardController, PasswordController}
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.utils._
import ContextUtils._

import scala.util.Try

class NewPasswordDialog extends DialogFragment with FragmentHelper {
  import NewPasswordDialog._

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.new_password_dialog, null)
  private lazy val strongPasswordValidator =
    PasswordValidator.createStrongPasswordValidator(BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH, BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH)
  private lazy val passwordEditText = findById[EditText](root, R.id.new_password_field)
  private lazy val newPasswordHint = returning(findById[TextView](root, R.id.new_password_hint)) { hint =>
    hint.setText(getString(R.string.password_policy_hint, BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH))
  }

  private lazy val showHideButton = returning(findById[ImageView](root, R.id.new_password_showhide)) { button =>
    var clicked = false

    def showOrHide(): Unit =
      button.setImageDrawable(getDrawable(
        if (isDarkTheme) {
          if (clicked) R.drawable.ic_visibility_off_white_18dp else R.drawable.ic_visibility_white_18dp
        } else {
          if (clicked) R.drawable.ic_visibility_off_black_18dp else R.drawable.ic_visibility_black_18dp
        }
      ))

    showOrHide()

    button.onClick {
      clicked = ! clicked
      showOrHide()
      if (clicked) {
        passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance)
      } else {
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance)
      }
      passwordEditText.setSelection(passwordEditText.getText.length);
    }
  }

  private lazy val mode = getStringArg(Mode).fold[Mode](SetMode)(getMode)
  private lazy val isDarkTheme = getBooleanArg(IsDarkTheme)
  private lazy val keyboard = inject[KeyboardController]

  override def onCreateDialog(savedInstanceState: Bundle): Dialog =
    ViewUtils.showAlertDialog(
      getActivity,
      root,
      mode.dialogTitleId,
      R.string.new_password_dialog_message,
      mode.dialogButtonId,
      android.R.string.cancel,
      null,
      null,
      mode == ChangeMode
    )

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
            newPasswordHint.setTextColor(getColor(R.color.accent_red))
          }
        }
      })
    }

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

  def newInstance(mode: Mode, isDarkTheme: Boolean): NewPasswordDialog = returning(new NewPasswordDialog){
    _.setArguments(returning(new Bundle()) { bundle =>
      bundle.putString(Mode, mode.id)
      bundle.putBoolean(IsDarkTheme, isDarkTheme)
    })
  }

  sealed trait Mode {
    val id: String
    val dialogTitleId: Int
    val dialogButtonId: Int
  }

  case object SetMode extends Mode {
    override val id: String = "Set"
    override val dialogTitleId: Int = R.string.new_password_dialog_title
    override val dialogButtonId: Int = R.string.new_password_dialog_button
  }

  case object ChangeMode extends Mode {
    override val id: String = "Change"
    override val dialogTitleId: Int = R.string.new_password_dialog_title_change
    override val dialogButtonId: Int = R.string.new_password_dialog_button_change
  }

  def getMode(id: String): Mode = id match {
    case SetMode.id    => SetMode
    case ChangeMode.id => ChangeMode
  }
}
