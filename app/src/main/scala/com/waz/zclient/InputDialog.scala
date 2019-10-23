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
package com.waz.zclient

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextWatcher
import android.view.View.OnAttachStateChangeListener
import android.view.{LayoutInflater, View}
import android.widget.{EditText, TextView}
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.utils.returning
import com.waz.zclient.utils.RichTextView

object InputDialog {

  trait Event
  case class OnPositiveBtn(input: String) extends Event
  case object OnNegativeBtn               extends Event

  trait ValidatorResult
  object ValidatorResult {
    case object Valid extends ValidatorResult
    case class Invalid(actions: Option[EditText => Unit] = None) extends ValidatorResult
  }

  trait InputValidator {
    /**
      * @return Non empty option with error message if input is invalid.
      */
    def isInputInvalid(input: String): ValidatorResult
  }

  private val Title                            = "TITLE"
  private val Message                          = "MESSAGE"
  private val Input                            = "INPUT"
  private val InputHint                        = "INPUT_HINT"
  private val ValidateInput                    = "VALIDATE_INPUT"
  private val DisablePositiveBtnOnInvalidInput = "DISABLE_POSITIVE_BTN"
  private val NegativeBtn                      = "NEGATIVE_BTN"
  private val PositiveBtn                      = "POSITIVE_BTN"

  trait Listener {
    def onDialogEvent(event: Event): Unit
  }

  def newInstance(@StringRes title: Int,
                  @StringRes message: Int,
                  inputValue: Option[String] = None,
                  @StringRes inputHint: Option[Int] = None,
                  validateInput: Boolean = false,
                  disablePositiveBtnOnInvalidInput: Boolean = false,
                  @StringRes negativeBtn: Int,
                  @StringRes positiveBtn: Int): InputDialog =
    returning(new InputDialog()) {
      _.setArguments(returning(new Bundle()) { bundle =>
        bundle.putInt(Title, title)
        bundle.putInt(Message, message)
        inputValue.foreach(i => bundle.putString(Input, i))
        inputHint.foreach(ih => bundle.putInt(InputHint, ih))
        bundle.putBoolean(ValidateInput, validateInput)
        bundle.putBoolean(DisablePositiveBtnOnInvalidInput, disablePositiveBtnOnInvalidInput)
        bundle.putInt(NegativeBtn, negativeBtn)
        bundle.putInt(PositiveBtn, positiveBtn)
      })
    }
}

class InputDialog extends DialogFragment with FragmentHelper {
  import InputDialog._

  private var listener : Option[Listener] = None
  private var validator: Option[InputValidator] = None

  def setListener(listener: Listener): this.type = {
    this.listener = Some(listener)
    this
  }

  def setValidator(validator: InputValidator): this.type = {
    this.validator = Some(validator)
    this
  }

  private lazy val view = LayoutInflater.from(getActivity).inflate(R.layout.dialog_with_input_field, null)
  private lazy val input = view.findViewById[EditText](R.id.input)
  private lazy val dialog =
    new AlertDialog.Builder(getContext)
      .setView(view)
      .setTitle(getArguments.getInt(Title))
      .setPositiveButton(getArguments.getInt(PositiveBtn), new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int): Unit =
          listener.foreach(_.onDialogEvent(OnPositiveBtn(input.getText.toString)))
      })
      .setNegativeButton(getArguments.getInt(NegativeBtn), new DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, which: Int): Unit =
          listener.foreach(_.onDialogEvent(OnNegativeBtn))
      })
      .create()

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    super.onCreateDialog(savedInstanceState)

    view
    input

    getIntArg(Message).foreach(view.findViewById[TextView](R.id.message).setText)
    getIntArg(InputHint).foreach(input.setHint)

    if (savedInstanceState == null) {
      getStringArg(Input).foreach(input.setText)
    }

    dialog
  }

  override def onStart(): Unit = {
    super.onStart()
    if (getBooleanArg(ValidateInput)) {
      input.addOnAttachStateChangeListener(onAttachStateChangeListener)
      positiveBtn.setEnabled(false)
      textWatcher = Option(input.addTextListener(validate))
    } else {
      positiveBtn.setEnabled(true)
    }
  }

  override def onStop(): Unit = {
    if (getBooleanArg(ValidateInput)) {
      input.removeOnAttachStateChangeListener(onAttachStateChangeListener)
      textWatcher.foreach(input.removeTextChangedListener)
      textWatcher = None
    }
    super.onStop()
  }

  private  def validate(str: String): Unit = validator.foreach(
    _.isInputInvalid(str) match {
      case ValidatorResult.Valid =>
        if (getBooleanArg(DisablePositiveBtnOnInvalidInput)) positiveBtn.setEnabled(true)
      case ValidatorResult.Invalid(actions) =>
        if (getBooleanArg(DisablePositiveBtnOnInvalidInput)) positiveBtn.setEnabled(false)
        actions.foreach(_(input))
    }
  )

  private def positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

  private val onAttachStateChangeListener = new OnAttachStateChangeListener {
    override def onViewDetachedFromWindow(v: View): Unit = {}
    override def onViewAttachedToWindow(v: View): Unit = validate(input.getText.toString)
  }

  private var textWatcher = Option.empty[TextWatcher]
}
