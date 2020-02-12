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

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.{Gravity, View}
import com.waz.api.EmailCredentials
import com.waz.model.AccountData.Password
import com.waz.model.{ConfirmationCode, EmailAddress}
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.PasswordValidator
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.{AppEntryDialogs, CreateTeamFragment}
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.SimpleValidator
import com.waz.zclient.tracking.TeamAcceptedTerms
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._

import scala.concurrent.Future

case class SetTeamPasswordFragment() extends CreateTeamFragment {

  import Threading.Implicits.Ui

  override val layoutId: Int = R.layout.set_password_scene
  private lazy val tracking = inject[TrackingService]

  private lazy val inputField = view[InputBox](R.id.input_field)

  private val passwordMinLength = BuildConfig.NEW_PASSWORD_MINIMUM_LENGTH
  private val passwordMaxLength = BuildConfig.NEW_PASSWORD_MAXIMUM_LENGTH
  private val validator = PasswordValidator.createStrongPasswordValidator(passwordMinLength, passwordMaxLength)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    inputField.foreach { inputField =>
      inputField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)

      // This validator is to enable the input field confirmation button. Strong validation
      // is enforced when this button is clicked.
      inputField.setValidator(SimpleValidator)

      // We need to adjust the behaviour of the error text view: It should always be visible,
      // but it will become red when validation fails.
      inputField.errorText.setGravity(Gravity.START)
      inputField.errorText.setTextColor(ContextUtils.getColor(R.color.teams_placeholder_text))
      inputField.setShouldDisableOnClick(false)
      inputField.setShouldClearErrorOnClick(false)
      inputField.setShouldClearErrorOnTyping(false)
      inputField.showErrorMessage(Some(getString(R.string.password_policy_hint, passwordMinLength)))

      inputField.editText.setText(createTeamController.password)

      inputField.editText.addTextListener { text =>
        createTeamController.password = text
        inputField.errorText.setTextColor(ContextUtils.getColor(R.color.teams_placeholder_text))
      }

      inputField.editText.requestFocus()
      KeyboardUtils.showKeyboard(context.asInstanceOf[Activity])

      inputField.setOnClick( text =>
        if (!validator.isValidPassword(text)) {
          inputField.errorText.setTextColor(ContextUtils.getColor(R.color.teams_error_red))
          Future.successful(Some(getString(R.string.password_policy_hint, passwordMinLength)))
        } else {
          AppEntryDialogs.showTermsAndConditions(context, inject[BrowserController]).flatMap {
            case true =>
              tracking.track(TeamAcceptedTerms(TeamAcceptedTerms.AfterPassword))
              val credentials = EmailCredentials(EmailAddress(createTeamController.teamEmail), Password(text), Some(ConfirmationCode(createTeamController.code)))

              accountsService.register(credentials, createTeamController.teamUserName, Some(createTeamController.teamName)).flatMap {
                case Left(error) =>
                  Future.successful(Some(getString(EmailError(error).bodyResource)))
                case Right(Some(am)) =>
                  am.initZMessaging()
                  am.addUnsplashPicture()
                  am.setMarketingConsent(createTeamController.receiveNewsAndOffers).map { _ =>
                    showFragment(InviteToTeamFragment(), InviteToTeamFragment.Tag)
                    None
                  }
                case _ => Future.successful(None)
              }
            case false =>
              Future.successful(None)
          }
        })
    }
  }
}

object SetTeamPasswordFragment {
  val Tag: String = getClass.getSimpleName
}
