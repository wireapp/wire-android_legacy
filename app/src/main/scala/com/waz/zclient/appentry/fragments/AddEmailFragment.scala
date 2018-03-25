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

import android.graphics.Color
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.zclient.appentry.controllers.AddEmailController
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.newreg.views.PhoneConfirmationButton.State.{CONFIRM, NONE}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.views.GuidedEditText
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}
import AddEmailFragment._
import com.waz.ZLog
import com.waz.zclient.appentry.EntryError
import com.waz.ZLog.ImplicitTag._
import com.waz.threading.Threading.Implicits.Ui
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Register, SignInMethod}

case class AddEmailFragment() extends BaseFragment[Container] with FragmentHelper with OnBackPressedListener {

  lazy val addEmailController = inject[AddEmailController]

  lazy val confirmationButton = view[PhoneConfirmationButton](R.id.confirmation_button)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.add_email_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    Option(findById[GuidedEditText](getView, R.id.email_field)).foreach { field =>
      field.setValidator(addEmailController.emailValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__email)
      addEmailController.email.head.map { field.setText }
      field.getEditText.addTextListener(addEmailController.email ! _)
    }

    Option(findById[GuidedEditText](getView, R.id.password_field)).foreach { field =>
      field.setValidator(addEmailController.passwordValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__password)
      addEmailController.password.head.map { field.setText }
      field.getEditText.addTextListener(addEmailController.password ! _)
    }

    confirmationButton.get.onClick {
      getContainer.enableProgress(true)
      addEmailController.addEmailAndPassword().map {
        case Left(error) =>
          getContainer.enableProgress(false)
          getContainer.showError(EntryError(error.getCode, error.getLabel, SignInMethod(Register, Email)))
        case _ =>
      }
    }
    confirmationButton.setAccentColor(Color.WHITE)
    setConfirmationButtonActive(addEmailController.isValid.currentValue.getOrElse(false))
    addEmailController.isValid.onUi { setConfirmationButtonActive }
  }


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    addEmailController.isValid.onUi { setConfirmationButtonActive }
  }

  private def setConfirmationButtonActive(active: Boolean): Unit =
    confirmationButton.setState(if(active) CONFIRM else NONE)

  override def onBackPressed(): Boolean = true
}

object AddEmailFragment {
  val Tag = ZLog.ImplicitTag.implicitLogTag
  trait Container {
    def enableProgress(enabled: Boolean): Unit
    def showError(entryError: EntryError, okCallback: => Unit = {}): Unit
  }
}
