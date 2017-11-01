/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.appentry

import android.os.{Build, Bundle, Handler}
import android.support.v4.content.ContextCompat
import android.text.{Editable, Html, TextWatcher}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{TextView, Toast}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.zclient._
import com.waz.zclient.controllers.SignInController.{Login, Phone, Register, SignInMethod}
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.text.TypefaceEditText
import com.waz.zclient.ui.utils.KeyboardUtils

object VerifyPhoneFragment {
  val TAG: String = classOf[VerifyPhoneFragment].getName
  private val ARG_SHOW_NOT_NOW: String = "ARG_SHOW_NOT_NOW"
  private val SHOW_RESEND_CODE_BUTTON_DELAY: Int = 15000
  private val RESEND_CODE_TIMER_INTERVAL: Int = 1000

  def newInstance(showNotNowButton: Boolean): VerifyPhoneFragment = {
    val fragment = new VerifyPhoneFragment
    val args = new Bundle
    args.putBoolean(ARG_SHOW_NOT_NOW, showNotNowButton)
    fragment.setArguments(args)
    fragment
  }

  trait Container {
    def enableProgress(enabled: Boolean): Unit
    def showError(entryError: EntryError, okCallback: => Unit = {}): Unit
  }

}

class VerifyPhoneFragment extends BaseFragment[VerifyPhoneFragment.Container] with FragmentHelper with View.OnClickListener with TextWatcher with OnBackPressedListener {

  implicit val executionContext = Threading.Ui

  private lazy val appEntryController = inject[AppEntryController]
  private lazy val tracking           = inject[GlobalTrackingController]

  private lazy val resendCodeButton = findById[TextView](getView, R.id.ttv__resend_button)
  private lazy val resendCodeTimer = findById[TextView](getView, R.id.ttv__resend_timer)
  private lazy val resendCodeCallButton = findById[View](getView, R.id.ttv__call_me_button)
  private lazy val editTextCode = findById[TypefaceEditText](getView, R.id.et__reg__code)
  private lazy val phoneConfirmationButton = findById[PhoneConfirmationButton](getView, R.id.pcb__activate)
  private lazy val buttonBack = findById[View](getView, R.id.ll__activation_button__back)
  private lazy val textViewInfo = findById[TextView](getView, R.id.ttv__info_text)
  private lazy val phoneVerificationCodeMinLength = getResources.getInteger(R.integer.new_reg__phone_verification_code__min_length)

  private var milliSecondsToShowResendButton = 0
  private lazy val resendCodeTimerHandler = new Handler
  private lazy val resendCodeTimerRunnable: Runnable = new Runnable() {
    def run(): Unit = {
      milliSecondsToShowResendButton = milliSecondsToShowResendButton - VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL
      if (milliSecondsToShowResendButton <= 0) {
        resendCodeTimer.setVisibility(View.GONE)
        resendCodeButton.setVisibility(View.VISIBLE)
        resendCodeCallButton.setVisibility(View.VISIBLE)
        return
      }
      val sec = milliSecondsToShowResendButton / 1000
      resendCodeTimer.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec)))
      resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL)
    }
  }


  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    findById[View](view, R.id.fl__confirmation_checkmark).setVisibility(View.GONE)
    findById[View](view, R.id.gtv__not_now__close).setVisibility(View.GONE)
    resendCodeButton.setVisibility(View.GONE)
    resendCodeCallButton.setVisibility(View.GONE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      editTextCode.setLetterSpacing(1)
    }
    appEntryController.currentAccount.map(_.flatMap(_.pendingPhone)).onUi{
      case Some(phone) =>
        onPhoneNumberLoaded(phone.str)
      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_phone__activation, container, false)
  }

  override def onStart(): Unit = {
    super.onStart()
    editTextCode.requestFocus
    val color = ContextCompat.getColor(getActivity, R.color.text__primary_dark)
    editTextCode.setAccentColor(color)
    phoneConfirmationButton.setAccentColor(color)
    resendCodeButton.setTextColor(color)
    textViewInfo.setTextColor(color)
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(Page.PHONE_VERIFY_CODE)
    KeyboardUtils.showKeyboard(getActivity)
    startResendCodeTimer()
  }

  override def onResume(): Unit = {
    super.onResume()
    phoneConfirmationButton.setOnClickListener(this)
    resendCodeButton.setOnClickListener(this)
    buttonBack.setOnClickListener(this)
    editTextCode.addTextChangedListener(this)
    resendCodeCallButton.setOnClickListener(this)
  }

  override def onPause(): Unit = {
    phoneConfirmationButton.setOnClickListener(null)
    resendCodeButton.setOnClickListener(null)
    buttonBack.setOnClickListener(null)
    editTextCode.removeTextChangedListener(this)
    resendCodeCallButton.setOnClickListener(null)
    super.onPause()
  }

  override def onStop(): Unit = {
    resendCodeTimerHandler.removeCallbacks(resendCodeTimerRunnable)
    KeyboardUtils.hideKeyboard(getActivity)
    super.onStop()
  }

  private def onPhoneNumberLoaded(phone: String): Unit = {
    val text = String.format(getResources.getString(R.string.activation_code_info_manual), phone)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
      //noinspection ScalaDeprecation
      textViewInfo.setText(Html.fromHtml(text))
    else
      textViewInfo.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY))

  }

  private def resendPhoneNumber(): Unit = {
    editTextCode.setText("")
    appEntryController.resendActivationPhoneCode().map {
      case Left(entryError) =>
        getContainer.showError(entryError)
        editTextCode.requestFocus
      case _ =>
        Toast.makeText(getActivity, getResources.getString(R.string.new_reg__code_resent), Toast.LENGTH_LONG).show()
    }
  }

  private def requestCallCode(): Unit = {
    appEntryController.resendActivationPhoneCode(shouldCall = true).map {
      case Left(entryError) =>
        getContainer.showError(entryError)
        editTextCode.requestFocus
      case _ =>
        Toast.makeText(getActivity, getResources.getString(R.string.new_reg__code_resent__call), Toast.LENGTH_LONG).show()
    }
  }

  private def goBack(): Unit = {
    appEntryController.cancelVerification()
  }

  private def confirmCode(): Unit = {
    getContainer.enableProgress(true)
    KeyboardUtils.hideKeyboard(getActivity)
    appEntryController.verifyPhone(editTextCode.getText.toString).map { result =>
      ZMessaging.currentAccounts.getActiveAccount.collect { case Some(acc) => acc.regWaiting }.map { reg =>
        tracking.onActivation(result, SignInMethod(if (reg) Register else Login, Phone))
      }
      result match {
        case Left(entryError) =>
          getContainer.enableProgress(false)
          getContainer.showError(entryError, {
            if (getActivity == null) return
            KeyboardUtils.showKeyboard(getActivity)
            editTextCode.requestFocus
            phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID)
          })
        case _ =>
          ZMessaging.currentAccounts.getActiveAccount.collect { case Some(acc) => acc.regWaiting }.map { reg =>
            tracking.onActivation(Right(()), SignInMethod(if (reg) Register else Login, Phone))
          }
      }
    }
  }

  def onClick(view: View): Unit = {
    view.getId match {
      case R.id.ll__activation_button__back =>
        goBack()
      case R.id.ttv__resend_button =>
        resendPhoneNumber()
        startResendCodeTimer()
      case R.id.pcb__activate =>
        confirmCode()
      case R.id.ttv__call_me_button =>
        requestCallCode()
        startResendCodeTimer()
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
    milliSecondsToShowResendButton = VerifyPhoneFragment.SHOW_RESEND_CODE_BUTTON_DELAY
    resendCodeButton.setVisibility(View.GONE)
    resendCodeCallButton.setVisibility(View.GONE)
    resendCodeTimer.setVisibility(View.VISIBLE)
    val sec = milliSecondsToShowResendButton / 1000
    resendCodeTimer.setText(getResources.getQuantityString(R.plurals.welcome__resend__timer_label, sec, Integer.valueOf(sec)))
    resendCodeTimerHandler.postDelayed(resendCodeTimerRunnable, VerifyPhoneFragment.RESEND_CODE_TIMER_INTERVAL)
  }

  override def onBackPressed() = {
    goBack()
    true
  }
}
