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

import android.os.{Build, Bundle, Handler}
import android.support.v4.content.ContextCompat
import android.text.{Editable, TextWatcher}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.api.EmailCredentials
import com.waz.content.GlobalPreferences
import com.waz.model.AccountData.Password
import com.waz.model.{ConfirmationCode, EmailAddress}
import com.waz.service.{AccountsService, GlobalModule}
import com.waz.service.tracking.TrackingService.track
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.fragments.SignInFragment.{Email, Register, SignInMethod}
import com.waz.zclient.appentry.fragments.VerifyEmailWithCodeFragment._
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.tracking.GlobalTrackingController._
import com.waz.zclient.tracking.{EnteredCodeEvent, RegistrationSuccessfulEvent}
import com.waz.zclient.ui.text.TypefaceEditText
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils

import scala.concurrent.Future

object VerifyEmailWithCodeFragment {
  val Tag: String = classOf[VerifyEmailWithCodeFragment].getName
  private val EmailArg: String = "email_arg"
  private val NameArg: String = "name_arg"
  private val PasswordArg: String = "password_arg"
  private val SHOW_RESEND_CODE_BUTTON_DELAY: Int = 15000
  private val RESEND_CODE_TIMER_INTERVAL: Int = 1000

  def apply(email: String, name: String, password: String): VerifyEmailWithCodeFragment =
    returning(new VerifyEmailWithCodeFragment) { f =>
      val args = new Bundle
      args.putString(EmailArg, email)
      args.putString(NameArg, name)
      args.putString(PasswordArg, password)
      f.setArguments(args)
    }

  trait Container {
    def enableProgress(enabled: Boolean): Unit
  }

}

class VerifyEmailWithCodeFragment extends FragmentHelper with View.OnClickListener with TextWatcher with OnBackPressedListener {

  implicit val executionContext = Threading.Ui
  implicit lazy val ctx = getContext

  private lazy val accountService     = inject[AccountsService]

  private lazy val resendCodeButton = findById[TextView](getView, R.id.ttv__resend_button)
  private lazy val resendCodeTimer = findById[TextView](getView, R.id.ttv__resend_timer)
  private lazy val editTextCode = findById[TypefaceEditText](getView, R.id.et__reg__code)
  private lazy val phoneConfirmationButton = findById[PhoneConfirmationButton](getView, R.id.pcb__activate)
  private lazy val buttonBack = findById[View](getView, R.id.ll__activation_button__back)
  private lazy val textViewInfo = findById[TextView](getView, R.id.ttv__info_text)
  private lazy val phoneVerificationCodeMinLength = getResources.getInteger(R.integer.new_reg__phone_verification_code__min_length)

  private var milliSecondsToShowResendButton = 0
  private lazy val resendCodeTimerHandler = new Handler
  private lazy val resendCodeTimerRunnable: Runnable = new Runnable() {
    def run(): Unit = {
      milliSecondsToShowResendButton = milliSecondsToShowResendButton - VerifyEmailWithCodeFragment.RESEND_CODE_TIMER_INTERVAL
      if (milliSecondsToShowResendButton <= 0) {
        resendCodeTimer.setVisibility(View.GONE)
        resendCodeButton.setVisibility(View.VISIBLE)
        return
      }
      val sec = milliSecondsToShowResendButton / 1000
      resendCodeTimer.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec)))
      resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyEmailWithCodeFragment.RESEND_CODE_TIMER_INTERVAL)
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    findById[View](view, R.id.fl__confirmation_checkmark).setVisibility(View.GONE)
    findById[View](view, R.id.gtv__not_now__close).setVisibility(View.GONE)
    resendCodeButton.setVisibility(View.GONE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      editTextCode.setLetterSpacing(1)
    }
    getStringArg(EmailArg).foreach { email =>
      val text = String.format(getResources.getString(R.string.activation_code_info_manual), email)
      textViewInfo.setText(DeprecationUtils.fromHtml(text))
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_email_code_activation, container, false)
  }

  override def onStart(): Unit = {
    super.onStart()
    editTextCode.requestFocus
    val color = ContextCompat.getColor(getActivity, R.color.text__primary_dark)
    editTextCode.setAccentColor(color)
    phoneConfirmationButton.setAccentColor(color)
    resendCodeButton.setTextColor(color)
    textViewInfo.setTextColor(color)
    inject[IGlobalLayoutController].setSoftInputModeForPage(Page.PHONE_VERIFY_CODE)
    KeyboardUtils.showKeyboard(getActivity)
    startResendCodeTimer()
  }

  override def onResume(): Unit = {
    super.onResume()
    phoneConfirmationButton.setOnClickListener(this)
    resendCodeButton.setOnClickListener(this)
    buttonBack.setOnClickListener(this)
    editTextCode.addTextChangedListener(this)
  }

  override def onPause(): Unit = {
    phoneConfirmationButton.setOnClickListener(null)
    resendCodeButton.setOnClickListener(null)
    buttonBack.setOnClickListener(null)
    editTextCode.removeTextChangedListener(this)
    super.onPause()
  }

  override def onStop(): Unit = {
    resendCodeTimerHandler.removeCallbacks(resendCodeTimerRunnable)
    KeyboardUtils.hideKeyboard(getActivity)
    super.onStop()
  }

  private def name = getStringArg(NameArg).getOrElse("")
  private def emailAddress = EmailAddress(getStringArg(EmailArg).getOrElse(""))
  private def password = Password(getStringArg(PasswordArg).getOrElse(""))
  private def confirmationCode = ConfirmationCode(editTextCode.getText.toString)

  private def requestCode() = {
    editTextCode.setText("")
    accountService.requestEmailCode(emailAddress).map {
      case Right(_) => showToast(R.string.new_reg__email_code_resent)
      case Left(err) =>
        showErrorDialog(EmailError(err))
        editTextCode.requestFocus
    }
  }

  private def goBack(): Unit = getFragmentManager.popBackStack()

  private def confirmCode(): Unit = {
    activity.enableProgress(true)
    KeyboardUtils.hideKeyboard(getActivity)

    for {
      resp <- accountService.register(EmailCredentials(emailAddress, password, Some(confirmationCode)), name)
      askMarketingConsent <- inject[GlobalModule].prefs(GlobalPreferences.ShowMarketingConsentDialog).apply()
      _    <- resp match {
        case Right(Some(am)) =>
          (if (!askMarketingConsent) Future.successful(Some(false)) else
            showConfirmationDialogWithNeutralButton(
              R.string.receive_news_and_offers_request_title,
              R.string.receive_news_and_offers_request_body,
              R.string.app_entry_dialog_privacy_policy,
              R.string.app_entry_dialog_accept,
              R.string.app_entry_dialog_no_thanks
            )).map { consent =>
            am.setMarketingConsent(consent)
            if (consent.isEmpty) inject[BrowserController].openUrl(getString(R.string.url_privacy_policy))
          }
        case _ => Future.successful({})
      }
    } yield {
      track(EnteredCodeEvent(SignInMethod(Register, Email), responseToErrorPair(resp)))
      resp match {
        case Left(error) =>
          activity.enableProgress(false)
          showErrorDialog(EmailError(error)).map { _ =>
            KeyboardUtils.showKeyboard(activity)
            editTextCode.requestFocus
            phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID)
          }
        case _ =>
          track(RegistrationSuccessfulEvent(SignInFragment.Email))
          activity.enableProgress(false)
          activity.onEnterApplication(openSettings = false)
      }
    }
  }

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.ll__activation_button__back =>
        goBack()
      case R.id.ttv__resend_button =>
        requestCode()
        startResendCodeTimer()
      case R.id.pcb__activate =>
        confirmCode()
    }
  }

  def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

  def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = {
    phoneConfirmationButton.setState(validatePhoneNumber(charSequence.toString))
  }

  def afterTextChanged(s: Editable): Unit = {}

  private def validatePhoneNumber(number: String): PhoneConfirmationButton.State = {
    if (number.length == phoneVerificationCodeMinLength)
      PhoneConfirmationButton.State.CONFIRM
    else
      PhoneConfirmationButton.State.NONE
  }

  private def startResendCodeTimer(): Unit = {
    milliSecondsToShowResendButton = VerifyEmailWithCodeFragment.SHOW_RESEND_CODE_BUTTON_DELAY
    resendCodeButton.setVisibility(View.GONE)
    resendCodeTimer.setVisibility(View.VISIBLE)
    val sec = milliSecondsToShowResendButton / 1000
    resendCodeTimer.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec)))
    resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyEmailWithCodeFragment.RESEND_CODE_TIMER_INTERVAL)
  }

  override def onBackPressed() = {
    goBack()
    true
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]
}
