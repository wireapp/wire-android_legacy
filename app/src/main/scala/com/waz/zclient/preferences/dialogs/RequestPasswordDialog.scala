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
import android.content.Context
import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import com.google.android.material.textfield.{TextInputEditText, TextInputLayout}
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.TextView
import androidx.biometric.{BiometricManager, BiometricPrompt}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, EventStream}
import com.waz.utils.returning
import com.waz.zclient.{BaseActivity, FragmentHelper, R}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.ExecutorWrapper
import com.waz.zclient.ui.utils.KeyboardUtils

import scala.util.Try
import scala.concurrent.duration._

class RequestPasswordDialog extends DialogFragment with FragmentHelper with DerivedLogTag {
  import RequestPasswordDialog._

  private val onPassword = EventStream[Password]()
  private val onBiometric = EventStream[BiometricAnswer]()

  private lazy val useBiometric = Option(getBooleanArg(UseBiometric)).getOrElse(false)
  private lazy val title = getStringArg(TitleArg).getOrElse("")
  private lazy val message = getStringArg(MessageArg).getOrElse("")
  private lazy val biometricDescription = getStringArg(BiometricDescriptionArg).getOrElse(message)
  private lazy val error = getStringArg(ErrorArg)

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.remove_otr_device_dialog, null)

  private lazy val passwordEditText = returning(findById[TextInputEditText](root, R.id.acet__remove_otr__password)) { v =>
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent) =
        actionId match {
          case EditorInfo.IME_ACTION_DONE =>
            sendPassword(v.getText.toString)
            true
          case _ => false
        }
    })
  }

  private def sendPassword(password: String): Unit = {
    onPassword ! Password(password)
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    dismiss()
  }

  private lazy val textInputLayout = findById[TextInputLayout](root, R.id.til__remove_otr_device)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    passwordEditText.requestFocus()
    textInputLayout

    error.foreach { err =>
      verbose(l"error: $err")
      textInputLayout.setError(err)
    }

    val builder = new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton(R.string.request_password_ok, null)

    Option(getBooleanArg(IsCancellable)).foreach(
      if (_) builder.setNegativeButton(R.string.request_password_cancel, null)
    )

    builder.create
  }

  override def onStart() = {
    super.onStart()
    verbose(l"onStart")
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { d =>
      d.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = sendPassword(passwordEditText.getText.toString)
      })
    }

    // FIXME: the delay is necessary because of a bug introduced in androidx.biometric:1.0.0-alpha04
    // https://stackoverflow.com/questions/55934108/fragmentmanager-is-already-executing-transactions-when-executing-biometricprompt
    // try to apply the proposed solution
    if (useBiometric && BiometricManager.from(getContext).canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS)
      CancellableFuture.delay(100.millis).map { _ => prompt.authenticate(promptInfo) }(Threading.Ui)
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }

  private lazy val promptInfo: BiometricPrompt.PromptInfo = new BiometricPrompt.PromptInfo.Builder()
    .setTitle(title)
    .setDescription(biometricDescription)
    .setNegativeButtonText(getString(R.string.request_password_biometric_cancel))
    .build

  private lazy val executor: ExecutorWrapper = ExecutorWrapper(Threading.Ui)

  private lazy val callback: BiometricPrompt.AuthenticationCallback = new BiometricPrompt.AuthenticationCallback {
    override def onAuthenticationError(errorCode: Int, errString: CharSequence): Unit = {
      super.onAuthenticationError(errorCode, errString)
      verbose(l"onAuthenticationError, code: $errorCode, str: $errString")
      // the cancellation code is 13; I couldn't find a constant for it
      onBiometric ! (if (errorCode == 13) BiometricAnswerCancelled else BiometricAnswerError(errString.toString))
      prompt.cancelAuthentication()
    }

    override def onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult): Unit = {
      super.onAuthenticationSucceeded(result)
      verbose(l"onAuthenticationSucceeded: $result")
      onBiometric ! BiometricAnswerSuccess
      KeyboardUtils.closeKeyboardIfShown(getActivity)
      dismiss()
    }

    override def onAuthenticationFailed(): Unit = {
      super.onAuthenticationFailed()
      verbose(l"onAuthenticationFailed")
      onBiometric ! BiometricAnswerFailure
    }
  }

  private lazy val prompt: BiometricPrompt = new BiometricPrompt(getActivity, executor, callback)
}

object RequestPasswordDialog {
  val Tag = RequestPasswordDialog.getClass.getSimpleName

  private val ErrorArg = "ARG_ERROR"
  private val IsCancellable = "IS_CANCELLABLE"
  private val UseBiometric = "USE_BIOMETRIC"

  private val TitleArg = "TITLE"
  private val MessageArg = "MESSAGE"
  private val BiometricDescriptionArg = "BIOMETRIC_DESCRIPTION"

  def apply(title:         String,
            onPassword:    Password => Unit,
            message:       Option[String]                  = None,
            biometricDesc: Option[String]                  = None,
            error:         Option[String]                  = None,
            isCancellable: Boolean                         = true,
            onBiometric:   Option[BiometricAnswer => Unit] = None
           )(implicit context: Context, evContext: EventContext): Unit = {
    val fragment = returning(new RequestPasswordDialog) { dialog =>
      dialog.setArguments(returning(new Bundle()) { b =>
        b.putString(TitleArg, title)
        message.foreach(b.putString(MessageArg, _))
        biometricDesc.foreach(b.putString(BiometricDescriptionArg, _))
        error.foreach(b.putString(ErrorArg, _))
        b.putBoolean(IsCancellable, isCancellable)
        b.putBoolean(UseBiometric, onBiometric.isDefined)
      })
      dialog.onPassword.onUi(onPassword)
      onBiometric.foreach(f => dialog.onBiometric.onUi(f))
      dialog.setCancelable(isCancellable)
    }
    context.asInstanceOf[BaseActivity].getSupportFragmentManager
      .beginTransaction
      .add(fragment, RequestPasswordDialog.Tag)
      .addToBackStack(RequestPasswordDialog.Tag)
      .commit
  }

  sealed trait BiometricAnswer
  case object BiometricAnswerSuccess extends BiometricAnswer
  case object BiometricAnswerFailure extends BiometricAnswer
  case object BiometricAnswerCancelled extends BiometricAnswer
  case class BiometricAnswerError(err: String) extends BiometricAnswer
}


