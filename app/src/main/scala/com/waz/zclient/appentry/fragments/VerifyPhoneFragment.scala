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
import com.waz.api.impl.ErrorResponse
import com.waz.model.{ConfirmationCode, PhoneNumber}
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.{returning, _}
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.appentry.DialogErrorMessage.PhoneError
import com.waz.zclient.appentry.fragments.SignInFragment.{Login, Phone, Register, SignInMethod}
import com.waz.zclient.appentry.fragments.VerifyPhoneFragment._
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.text.TypefaceEditText
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DeprecationUtils

import scala.concurrent.Future

object VerifyPhoneFragment {
  val Tag: String = classOf[VerifyPhoneFragment].getName
  private val PhoneArg: String = "phone_arg"
  private val LoggingInArg: String = "logging_in_arg"
  private val SHOW_RESEND_CODE_BUTTON_DELAY: Int = 15000
  private val RESEND_CODE_TIMER_INTERVAL: Int = 1000

  def apply(phone: String, login: Boolean): VerifyPhoneFragment =
    returning(new VerifyPhoneFragment) { f =>
      val args = new Bundle
      args.putBoolean(LoggingInArg, login)
      args.putString(PhoneArg, phone)
      f.setArguments(args)
    }
}

class VerifyPhoneFragment extends FragmentHelper with View.OnClickListener with TextWatcher with OnBackPressedListener {

  import Threading.Implicits.Ui
  private lazy val accountService = inject[AccountsService]
  private lazy val tracking       = inject[GlobalTrackingController]

  private lazy val resendCodeButton = view[TextView](R.id.ttv__resend_button)
  private lazy val resendCodeTimer = view[TextView](R.id.ttv__resend_timer)
  private lazy val resendCodeCallButton = view[View](R.id.ttv__call_me_button)
  private lazy val editTextCode = view[TypefaceEditText](R.id.et__reg__code)
  private lazy val phoneConfirmationButton = view[PhoneConfirmationButton](R.id.pcb__activate)
  private lazy val buttonBack = view[View](R.id.ll__activation_button__back)
  private lazy val textViewInfo = view[TextView](R.id.ttv__info_text)
  private lazy val phoneVerificationCodeMinLength = getResources.getInteger(R.integer.new_reg__phone_verification_code__min_length)

  private var milliSecondsToShowResendButton = 0
  private lazy val resendCodeTimerHandler = new Handler
  private lazy val resendCodeTimerRunnable: Runnable = new Runnable() {
    def run(): Unit = {
      milliSecondsToShowResendButton = milliSecondsToShowResendButton - VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL
      if (milliSecondsToShowResendButton <= 0) {
        resendCodeTimer.foreach(_.setVisibility(View.GONE))
        resendCodeButton.foreach(_.setVisibility(View.VISIBLE))
        resendCodeCallButton.foreach(_.setVisibility(View.VISIBLE))
        return
      }
      val sec = milliSecondsToShowResendButton / 1000
      resendCodeTimer.foreach(_.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec))))
      resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL)
    }
  }


  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    findById[View](view, R.id.fl__confirmation_checkmark).setVisibility(View.GONE)
    findById[View](view, R.id.gtv__not_now__close).setVisibility(View.GONE)
    resendCodeButton.foreach(_.setVisibility(View.GONE))
    resendCodeCallButton.foreach(_.setVisibility(View.GONE))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      editTextCode.foreach(_.setLetterSpacing(1))
    }
    getStringArg(PhoneArg).foreach(phone => onPhoneNumberLoaded(phone))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_phone__activation, container, false)
  }

  override def onStart(): Unit = {
    super.onStart()
    editTextCode.foreach(_.requestFocus)
    val color = ContextCompat.getColor(getActivity, R.color.text__primary_dark)
    editTextCode.foreach(_.setAccentColor(color))
    phoneConfirmationButton.foreach(_.setAccentColor(color))
    resendCodeButton.foreach(_.setTextColor(color))
    textViewInfo.foreach(_.setTextColor(color))
    inject[IGlobalLayoutController].setSoftInputModeForPage(Page.PHONE_VERIFY_CODE)
    KeyboardUtils.showKeyboard(getActivity)
    startResendCodeTimer()
  }

  override def onResume(): Unit = {
    super.onResume()
    editTextCode.map(_.getText.toString).foreach(text => onTextChanged(text, 0, text.length, text.length))
    phoneConfirmationButton.foreach(_.setOnClickListener(this))
    resendCodeButton.foreach(_.setOnClickListener(this))
    buttonBack.foreach(_.setOnClickListener(this))
    editTextCode.foreach(_.addTextChangedListener(this))
    resendCodeCallButton.foreach(_.setOnClickListener(this))
  }

  override def onPause(): Unit = {
    phoneConfirmationButton.foreach(_.setOnClickListener(null))
    resendCodeButton.foreach(_.setOnClickListener(null))
    buttonBack.foreach(_.setOnClickListener(null))
    editTextCode.foreach(_.removeTextChangedListener(this))
    resendCodeCallButton.foreach(_.setOnClickListener(null))
    super.onPause()
  }

  override def onStop(): Unit = {
    resendCodeTimerHandler.removeCallbacks(resendCodeTimerRunnable)
    KeyboardUtils.hideKeyboard(getActivity)
    super.onStop()
  }

  private def onPhoneNumberLoaded(phone: String): Unit = {
    val text = String.format(getResources.getString(R.string.activation_code_info_manual), phone)
    textViewInfo.foreach(_.setText(DeprecationUtils.fromHtml(text)))
  }

  private def requestCode(shouldCall: Boolean) = {
    editTextCode.foreach(_.setText(""))
    val isLoggingIn = getBooleanArg(LoggingInArg, default = true)
    val phone = getStringArg(PhoneArg).getOrElse("")
    accountService.requestPhoneCode(PhoneNumber(phone), login = isLoggingIn, call = shouldCall).map { result =>
      tracking.onRequestResendCode(result, SignInMethod(if (isLoggingIn) Login else Register, Phone), isCall = shouldCall)
      result match {
        case Left(err) =>
          showErrorDialog(PhoneError(err))
          editTextCode.foreach(_.requestFocus)
        case _ =>
          showToast(if (shouldCall) R.string.new_reg__code_resent__call else R.string.new_reg__code_resent)
      }
    }
  }

  private def goBack(): Unit = getFragmentManager.popBackStack()

  private def confirmCode(): Unit = {
    activity.enableProgress(true)
    KeyboardUtils.hideKeyboard(getActivity)

    val isLoggingIn = getBooleanArg(LoggingInArg, default = true)
    val phone = getStringArg(PhoneArg).getOrElse("")
    val code = editTextCode.map(_.getText.toString).getOrElse("")

    def onError(err: ErrorResponse) = {
      activity.enableProgress(false)
      showErrorDialog(PhoneError(err)).map { _ =>
        if (getActivity != null) {
          KeyboardUtils.showKeyboard(getActivity)
          editTextCode.foreach(_.requestFocus)
          phoneConfirmationButton.foreach(_.setState(PhoneConfirmationButton.State.INVALID))
        }
      }
    }

    if (isLoggingIn) {
      accountService.loginPhone(phone, code).map {
        case Left(error) => onError(error)
        case Right(userId) =>
          activity.enableProgress(false)
          for {
            am <- accountService.createAccountManager(userId, None, isLogin = Some(true))
            _ <- accountService.setAccount(Some(userId))
            regState <- am.fold2(Future.successful(Left(ErrorResponse.internalError(""))), _.getOrRegisterClient())
          } yield activity.onEnterApplication(openSettings = false, regState.fold(_ => None, Some(_)))
      }
    } else {
      accountService.verifyPhoneNumber(PhoneNumber(phone), ConfirmationCode(code), dryRun = true).foreach {
        case Left(error) => onError(error)
        case _ =>
          activity.enableProgress(false)
          activity.showFragment(PhoneSetNameFragment(phone, code), PhoneSetNameFragment.Tag)
      }
    }
  }

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.ll__activation_button__back =>
        goBack()
      case R.id.ttv__resend_button =>
        requestCode(shouldCall = false)
        startResendCodeTimer()
      case R.id.pcb__activate =>
        confirmCode()
      case R.id.ttv__call_me_button =>
        requestCode(shouldCall = true)
        startResendCodeTimer()
    }
  }

  def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

  def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = {
    phoneConfirmationButton.foreach(_.setState(validatePhoneNumber(charSequence.toString)))
  }

  def afterTextChanged(s: Editable): Unit = {}

  private def validatePhoneNumber(number: String): PhoneConfirmationButton.State = {
    if (number.length == phoneVerificationCodeMinLength)
      PhoneConfirmationButton.State.CONFIRM
    else
      PhoneConfirmationButton.State.NONE
  }

  private def startResendCodeTimer(): Unit = {
    milliSecondsToShowResendButton = VerifyPhoneFragment.SHOW_RESEND_CODE_BUTTON_DELAY
    resendCodeButton.foreach(_.setVisibility(View.GONE))
    resendCodeCallButton.foreach(_.setVisibility(View.GONE))
    resendCodeTimer.foreach(_.setVisibility(View.VISIBLE))
    val sec = milliSecondsToShowResendButton / 1000
    resendCodeTimer.foreach(_.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec))))
    resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL)
  }

  override def onBackPressed() = {
    goBack()
    true
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]
}
