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
import android.text.{Editable, TextWatcher}
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View}
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.{TextInputEditText, TextInputLayout}
import com.waz.utils.returning

object InputDialog {

  trait Event
  case class  OnPositiveBtn(input: String) extends Event
  case object OnNegativeBtn                extends Event

  private val Title                            = "TITLE"
  private val Message                          = "MESSAGE"
  private val Input                            = "INPUT"
  private val InputHint                        = "INPUT_HINT"
  private val NegativeBtn                      = "NEGATIVE_BTN"
  private val PositiveBtn                      = "POSITIVE_BTN"

  trait Listener {
    def onTextChanged(text: String): Unit
    def onDialogEvent(event: Event): Unit
  }

  def newInstance(@StringRes title: Int,
                  @StringRes message: Int,
                  inputValue: Option[String] = None,
                  @StringRes inputHint: Option[Int] = None,
                  @StringRes negativeBtn: Int,
                  @StringRes positiveBtn: Int): InputDialog =
    returning(new InputDialog()) {
      _.setArguments(returning(new Bundle()) { bundle =>
        bundle.putInt(Title, title)
        bundle.putInt(Message, message)
        inputValue.foreach(i => bundle.putString(Input, i))
        inputHint.foreach(ih => bundle.putInt(InputHint, ih))
        bundle.putInt(NegativeBtn, negativeBtn)
        bundle.putInt(PositiveBtn, positiveBtn)
      })
    }
}

class InputDialog extends DialogFragment with FragmentHelper {
  import InputDialog._

  private var listener : Option[Listener] = None
  private var textWatcher: Option[TextWatcher] = None

  def setListener(listener: Listener): this.type = {
    this.listener = Some(listener)
    initTextWatcher()
    this
  }

  private lazy val view = LayoutInflater.from(getActivity).inflate(R.layout.dialog_with_input_field, null)
  private lazy val input = view.findViewById[TextInputEditText](R.id.input)
  private lazy val textInputLayout = view.findViewById[TextInputLayout](R.id.dialogWithInputTextInputLayout)
  private lazy val dialog =
    returning(new AlertDialog.Builder(getContext)
      .setView(view)
      .setTitle(getArguments.getInt(Title))
      .setPositiveButton(getArguments.getInt(PositiveBtn), null)
      .setNegativeButton(getArguments.getInt(NegativeBtn), null)
      .create()
    ) { alertDialog =>
          alertDialog.setOnShowListener(new DialogInterface.OnShowListener {
            override def onShow(dialog: DialogInterface): Unit = {
              alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new OnClickListener {
                override def onClick(v: View): Unit = {
                  listener.foreach(_.onDialogEvent(OnPositiveBtn(input.getText.toString)))
                }
              })

              alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new OnClickListener {
                override def onClick(v: View): Unit = {
                  listener.foreach(_.onDialogEvent(OnNegativeBtn))
                }
              })
            }
          })
      }

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

  private def initTextWatcher(): Unit = {
    textWatcher = Some(new TextWatcher {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit =
        listener.foreach(_.onTextChanged(s.toString))

      override def afterTextChanged(s: Editable): Unit = {}
    })
  }

  override def onStart(): Unit = {
    super.onStart()
    textWatcher.foreach(input.addTextChangedListener)
  }

  override def onStop(): Unit = {
    textWatcher.foreach(input.removeTextChangedListener)
    super.onStop()
  }

  def clearError() = textInputLayout.setErrorEnabled(false)

  def setError(errorText: String)= {
    textInputLayout.setErrorEnabled(true)
    textInputLayout.setError(errorText)
  }
}
