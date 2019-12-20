/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
import android.content.DialogInterface
import android.content.DialogInterface.{BUTTON_POSITIVE, OnClickListener}
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, WindowManager}
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.biometric.{BiometricConstants, BiometricManager, BiometricPrompt}
import androidx.fragment.app.{DialogFragment, FragmentActivity}
import com.google.android.material.textfield.{TextInputEditText, TextInputLayout}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, EventStream}
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.ExecutorWrapper
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.duration._
import scala.util.Try

class RequestPasswordDialog extends DialogFragment with FragmentHelper with DerivedLogTag {
  import RequestPasswordDialog._

  private val onAnswer = EventStream[PromptAnswer]()

  private lazy val useBiometric = Option(getBooleanArg(UseBiometric)).getOrElse(false) &&
    BiometricManager.from(getContext).canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
  private lazy val title                = getStringArg(TitleArg).getOrElse("")
  private lazy val message              = getStringArg(MessageArg).getOrElse("")
  private lazy val biometricDescription = getStringArg(BiometricDescriptionArg).getOrElse(message)

  private lazy val root = LayoutInflater.from(getActivity).inflate(R.layout.remove_otr_device_dialog, null)

  private lazy val passwordEditText = returning(findById[TextInputEditText](root, R.id.acet__remove_otr__password)) { v =>
    v.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      def onEditorAction(v: TextView, actionId: Int, event: KeyEvent) =
        actionId match {
          case EditorInfo.IME_ACTION_DONE =>
            onAnswer ! PasswordAnswer(Password(v.getText.toString))
            true
          case _ => false
        }
    })
  }

  private lazy val errorLayout = findById[TextInputLayout](root, R.id.til__remove_otr_device)

  private lazy val promptInfo: BiometricPrompt.PromptInfo = new BiometricPrompt.PromptInfo.Builder()
    .setTitle(title)
    .setDescription(biometricDescription)
    .setNegativeButtonText(getString(R.string.request_password_biometric_cancel))
    .build

  private lazy val prompt: BiometricPrompt = new BiometricPrompt(getActivity, ExecutorWrapper(Threading.Ui), new BiometricPrompt.AuthenticationCallback {
    override def onAuthenticationError(errorCode: Int, errString: CharSequence): Unit = {
      super.onAuthenticationError(errorCode, errString)
      verbose(l"onAuthenticationError, code: $errorCode, str: $errString")
      onAnswer ! (if (errorCode == BiometricConstants.ERROR_NEGATIVE_BUTTON) BiometricCancelled else BiometricError(errString.toString))
    }

    override def onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult): Unit = {
      super.onAuthenticationSucceeded(result)
      verbose(l"onAuthenticationSucceeded: $result")
      onAnswer ! BiometricSuccess
    }

    override def onAuthenticationFailed(): Unit = {
      super.onAuthenticationFailed()
      verbose(l"onAuthenticationFailed")
      onAnswer ! BiometricFailure
    }
  })

  def show(activity: FragmentActivity): Unit =
    activity.getSupportFragmentManager
      .beginTransaction
      .add(this, RequestPasswordDialog.Tag)
      .addToBackStack(RequestPasswordDialog.Tag)
      .commit

  def close(): Unit = {
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    dismiss()
  }

  def cancelBiometric(): Unit = {
    prompt.cancelAuthentication()
    getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    passwordEditText.requestFocus()
  }

  def clearText(): Unit = passwordEditText.setText("")

  def showError(error: Option[String]): Unit = error match {
    case Some(err) => errorLayout.setError(err)
    case None      => errorLayout.setErrorEnabled(false)
  }

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    if (!useBiometric) passwordEditText.requestFocus()
    errorLayout

    val builder = new AlertDialog.Builder(getActivity)
      .setView(root)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton(R.string.request_password_ok, null)

    Option(getBooleanArg(IsCancellable)).foreach(
      if (_) builder.setNegativeButton(R.string.request_password_cancel, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = onAnswer ! PasswordCancelled
      })
    )

    builder.create
  }

  override def onStart() = {
    super.onStart()
    verbose(l"onStart")
    Try(getDialog.asInstanceOf[AlertDialog]).toOption.foreach { d =>
      d.getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = onAnswer ! PasswordAnswer(Password(passwordEditText.getText.toString))
      })
    }

    // FIXME: the delay is necessary because of a bug introduced in androidx.biometric:1.0.0-alpha04
    // https://stackoverflow.com/questions/55934108/fragmentmanager-is-already-executing-transactions-when-executing-biometricprompt
    // try to apply the proposed solution
    if (useBiometric) CancellableFuture.delay(100.millis).map { _ => prompt.authenticate(promptInfo) }(Threading.Ui)
  }

  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    if (!useBiometric) getDialog.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    //hide content of wire when it's locked
    getDialog.getWindow.setDimAmount(1.0f)
  }
}

object RequestPasswordDialog {
  val Tag = RequestPasswordDialog.getClass.getSimpleName

  private val IsCancellable           = "IS_CANCELLABLE"
  private val UseBiometric            = "USE_BIOMETRIC"
  private val TitleArg                = "TITLE"
  private val MessageArg              = "MESSAGE"
  private val BiometricDescriptionArg = "BIOMETRIC_DESCRIPTION"

  def apply(title:         String,
            onAnswer:      PromptAnswer => Unit,
            message:       Option[String] = None,
            biometricDesc: Option[String] = None,
            isCancellable: Boolean        = true,
            useBiometric:  Boolean        = false
           )(implicit evContext: EventContext): RequestPasswordDialog =
    returning(new RequestPasswordDialog) { dialog =>
      dialog.setArguments(returning(new Bundle()) { b =>
        b.putString(TitleArg, title)
        message.foreach(b.putString(MessageArg, _))
        biometricDesc.foreach(b.putString(BiometricDescriptionArg, _))
        b.putBoolean(IsCancellable, isCancellable)
        b.putBoolean(UseBiometric, useBiometric)
      })
      dialog.onAnswer.onUi(onAnswer)
      dialog.setCancelable(isCancellable)
    }

  sealed trait PromptAnswer
  case object BiometricSuccess extends PromptAnswer
  case object BiometricFailure extends PromptAnswer
  case object BiometricCancelled extends PromptAnswer
  case object PasswordCancelled extends PromptAnswer
  case class BiometricError(err: String) extends PromptAnswer
  case class PasswordAnswer(password: Password) extends PromptAnswer
}
