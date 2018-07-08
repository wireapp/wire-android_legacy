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
package com.waz.zclient.appentry.fragments

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.{Editable, TextWatcher}
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.api.PhoneCredentials
import com.waz.model.{ConfirmationCode, PhoneNumber}
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.DialogErrorMessage.PhoneError
import com.waz.zclient.appentry.fragments.PhoneSetNameFragment._
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.ui.text.TypefaceEditText
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.showErrorDialog

object PhoneSetNameFragment {
  val Tag = classOf[PhoneSetNameFragment].getName

  private val PhoneArg: String = "phone_arg"
  private val CodeArg: String = "code_arg"

  def apply(phone: String, code: String): PhoneSetNameFragment =
    returning(new PhoneSetNameFragment) { f =>
      val args = new Bundle
      args.putString(PhoneArg, phone)
      args.putString(CodeArg, code)
      f.setArguments(args)
    }
}

class PhoneSetNameFragment extends FragmentHelper with TextWatcher with View.OnClickListener {

  implicit val executionContext = Threading.Ui

  private lazy val accountsService = inject[AccountsService]

  private lazy val editTextName = view[TypefaceEditText](R.id.et__reg__name)
  private lazy val nameConfirmationButton = view[PhoneConfirmationButton](R.id.pcb__signup)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_phone__name, container, false)
  }

  override def onStart(): Unit = {
    super.onStart()
    editTextName.foreach(_.requestFocus)
    nameConfirmationButton.foreach(_.setAccentColor(ContextCompat.getColor(getActivity, R.color.text__primary_dark)))
  }

  override def onResume(): Unit = {
    super.onResume()
    nameConfirmationButton.foreach(_.setOnClickListener(this))
    editTextName.foreach { editTextName =>
      editTextName.addTextChangedListener(this)
      editTextName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
        def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
          if (actionId == EditorInfo.IME_ACTION_DONE && isNameValid(editTextName.getText.toString)) {
            confirmName()
            true
          } else {
            false
          }
        }
      })
    }
    inject[IGlobalLayoutController].setSoftInputModeForPage(Page.PHONE_REGISTRATION_ADD_NAME)
    KeyboardUtils.showKeyboard(getActivity)
  }

  override def onPause(): Unit = {
    editTextName.foreach(_.removeTextChangedListener(this))
    nameConfirmationButton.foreach(_.setOnClickListener(null))
    super.onPause()
  }

  override def onStop(): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    super.onStop()
  }

  private def confirmName(): Unit = {
    activity.enableProgress(true)
    KeyboardUtils.hideKeyboard(getActivity)

    val phone = getStringArg(PhoneArg).getOrElse("")
    val code = getStringArg(CodeArg).getOrElse("")
    val name = editTextName.map(_.getText.toString).getOrElse("")

    accountsService.register(PhoneCredentials(PhoneNumber(phone), ConfirmationCode(code)), name).map {
      case Left(error) =>
        activity.enableProgress(false)
        showErrorDialog(PhoneError(error)).foreach { _ =>
          if (getActivity == null) return
          KeyboardUtils.showKeyboard(getActivity)
          editTextName.foreach(_.requestFocus)
          nameConfirmationButton.foreach(_.setState(PhoneConfirmationButton.State.INVALID))
        }
      case _ =>
        activity.enableProgress(false)
        activity.onEnterApplication(false)
    }
  }

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.pcb__signup =>
        confirmName()
    }
  }

  def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

  def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = {
    nameConfirmationButton.foreach(_.setState(validateName(charSequence.toString)))
  }

  def afterTextChanged(s: Editable): Unit = {}

  private def validateName(name: String): PhoneConfirmationButton.State = {
    if (isNameValid(name))
      PhoneConfirmationButton.State.CONFIRM
    else
      PhoneConfirmationButton.State.NONE
  }

  private def isNameValid(name: String): Boolean = name != null && name.trim.length > 1

  override def onBackPressed(): Boolean = {
    getFragmentManager.popBackStack()
    true
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]
}
