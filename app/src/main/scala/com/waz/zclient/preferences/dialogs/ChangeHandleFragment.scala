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

import java.util.Locale
import android.os.Bundle
import android.text.{Editable, TextWatcher}
import android.view.View.OnClickListener
import android.view.animation.AnimationUtils
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.waz.model.{DisplayHandleDomainPolicies, Domain, Handle}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.{FragmentHelper, HandleLength, R}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.DeprecationUtils

import scala.util.Try

class ChangeHandleFragment extends DialogFragment with FragmentHelper {
  import ChangeHandleFragment._
  import Threading.Implicits.Ui

  private var usernameInputLayout:      TextInputLayout = _
  private var handleEditText:           AppCompatEditText = _
  private var handleVerifyingIndicator: LoadingIndicatorView = _
  private var okButton:                 View = _
  private var backButton:               View = _

  private var inputHandle:     String = ""
  private var suggestedHandle: String = _
  private var editingEnabled:  Boolean = true
  private var cancelEnabled:   Boolean = true

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val users = zms.map(_.users)
  private lazy val currentHandle = users.flatMap(_.selfUser.map(_.displayHandle(Domain.Empty, DisplayHandleDomainPolicies.NeverShowDomain)))

  private val handleTextWatcher = new TextWatcher() {
    private var lastText: String = ""

    def beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = {
      val currentText = charSequence.toString
      if (validateUsername(currentText) != ValidationError.InvalidCharacters) lastText = currentText
    }

    def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) = {}

    def afterTextChanged(editable: Editable) = {
      import ValidationError._
      val normalText = editable.toString
      val lowercaseString = normalText.toLowerCase(Locale.getDefault)

      if (lowercaseString != normalText) handleEditText.setText(lowercaseString)
      else {
        val error = validateUsername(normalText)
        setErrorMessage(error)
        error match {
          case NoError =>
            for {
              z         <- zms.head
              curHandle <- currentHandle.head
              if !curHandle.contains(normalText)
              _ <- z.handlesClient.isUserHandleAvailable(Handle.from(normalText)).map {
                case Right(true) =>
                  setErrorMessage("")
                  okButton.setEnabled(editingEnabled)
                case Right(false) =>
                  setErrorMessage(AlreadyTaken)
                  okButton.setEnabled(false)
                case _ =>
                  setErrorMessage(R.string.pref__account_action__dialog__change_username__error_unknown)
                  enableEditing()
                  editBoxShakeAnimation()
              }
            } yield {}

          case InvalidCharacters =>
            handleEditText.setText(lastText)
            editBoxShakeAnimation()
          case TooLong =>
            handleEditText.setText(lastText)
          case _ =>
        }
      }
      handleEditText.setSelection(handleEditText.getText.length)
    }
  }

  private val okButtonClickListener = new OnClickListener() {
    def onClick(view: View) = {
      inputHandle = handleEditText.getText.toString

      Option(inputHandle).filter(_.nonEmpty).foreach { input =>
        currentHandle.head.map {
          case handle if handle == input => dismiss()
          case _ =>
            import ValidationError._
            validateUsername(input) match {
              case NoError =>
                disableEditing()
                updateHandle(inputHandle).map {
                  case Right(_) =>
                    dismiss()

                  case Left(err) =>
                    warn(l"Failed to update username: $err")
                    setErrorMessage(R.string.pref__account_action__dialog__change_username__error_unknown)
                    enableEditing()
                }

              case err =>
                setErrorMessage(err)
                enableEditing()
                editBoxShakeAnimation()
            }
        }
      }
    }
  }

  private val backButtonClickListener = new OnClickListener {
    override def onClick(v: View): Unit = {
      if (!cancelEnabled) {
        updateHandle(suggestedHandle)
      }
      dismiss()
    }
  }

  private def disableEditing() = {
    editingEnabled = false
    handleEditText.setEnabled(false)
    handleVerifyingIndicator.show(LoadingIndicatorView.Spinner)
    okButton.setEnabled(false)
    backButton.setEnabled(false)
  }

  private def enableEditing() = {
    editingEnabled = true
    handleVerifyingIndicator.hide()
    okButton.setEnabled(true)
    backButton.setEnabled(true)
    handleEditText.setEnabled(true)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    suggestedHandle = getArguments.getString(ArgHandle, "")
    cancelEnabled   = getArguments.getBoolean(ArgCancelEnabled)

    returning(inflater.inflate(R.layout.fragment_change_username_preference_dialog, container, false)) { layout =>
      usernameInputLayout = findById[TextInputLayout](layout, R.id.til__change_username)
      handleEditText = returning(findById[AppCompatEditText](layout, R.id.acet__change_username)) { et =>
        et.setText(suggestedHandle)
        et.setSelection(suggestedHandle.length)
      }
      handleVerifyingIndicator = returning(findById[LoadingIndicatorView](layout, R.id.liv__username_verifying_indicator)) { _.hide() }

      okButton   = findById[View](layout, R.id.tv__ok_button)
      backButton = findById[View](layout, R.id.tv__back_button)

      setCancelable(false)
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    Try(getDialog.getWindow).foreach {
      DeprecationUtils.setSoftInputMode(_, true, false)
    }
    super.onViewCreated(view, savedInstanceState)
  }

  override def onStart(): Unit = {
    super.onStart()
    handleEditText.addTextChangedListener(handleTextWatcher)
    backButton.setOnClickListener(backButtonClickListener)
    okButton.setOnClickListener(okButtonClickListener)
    handleEditText.requestFocus
  }

  override def onStop() = {
    handleEditText.removeTextChangedListener(handleTextWatcher)
    backButton.setOnClickListener(null)
    okButton.setOnClickListener(null)
    super.onStop()
  }

  private def setErrorMessage(error: ValidationError): Unit = {
    import ValidationError._
    val str = error match {
      case NoError           => " "
      case TooLong           => " "
      case TooShort          => " "
      case InvalidCharacters => " "
      case AlreadyTaken      => getString(R.string.pref__account_action__dialog__change_username__error_already_taken)
      case _                 => getString(R.string.pref__account_action__dialog__change_username__error_unknown)
    }
    setErrorMessage(str)
  }

  private def setErrorMessage(resId: Int): Unit =
    setErrorMessage(getString(resId))

  private def setErrorMessage(str: String): Unit =
    Option(getActivity).filter(_ => isAdded).foreach(_ => usernameInputLayout.setError(str))

  private def editBoxShakeAnimation() =
    handleEditText.startAnimation(AnimationUtils.loadAnimation(getContext, R.anim.shake_animation))

  private def updateHandle(handle: String) =
    users.head.flatMap(_.updateHandle(Handle.from(handle)))
}

object ChangeHandleFragment {

  val Tag: String = getClass.getSimpleName

  private val ArgCancelEnabled = "ARG_CANCEL_ENABLED"
  private val ArgHandle        = "ARG_HANDLE"

  def newInstance(existingHandle: String, cancellable: Boolean) = {
    returning(new ChangeHandleFragment) {
      _.setArguments(returning(new Bundle()) { b =>
        b.putBoolean(ArgCancelEnabled, cancellable)
        b.putString(ArgHandle, existingHandle)
      })
    }
  }

  type ValidationError = ValidationError.Value
  object ValidationError extends Enumeration {
    val InvalidCharacters, AlreadyTaken, TooLong, TooShort, NoError = Value
  }

  val MaxLength = HandleLength.HandleMaxLength
  val MinLength = HandleLength.HandleMinLength
  val checkMultipleAvailabilityPath = "/users"
  val checkSingleAvailabilityPath = "/users/handles/"
  val handlesQuery = "handles"

  val ValidUsername = s"""^([a-z]|[0-9]|_)*""".r

  private def validateUsername(username: String): ValidationError = {
    import ValidationError._
    username match {
      case ValidUsername(_) =>
        username.length match {
          case l if l > MaxLength => TooLong
          case l if l < MinLength => TooShort
          case _ => NoError
        }
      case _ => InvalidCharacters
    }
  }
}
