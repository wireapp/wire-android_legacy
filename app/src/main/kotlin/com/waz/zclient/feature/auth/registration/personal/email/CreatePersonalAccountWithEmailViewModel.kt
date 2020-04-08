package com.waz.zclient.feature.auth.registration.personal.email

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.shared.activation.usecase.ActivateEmailParams
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeParams
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.ValidateEmailError
import com.waz.zclient.shared.user.email.ValidateEmailParams
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountWithEmailViewModel(
	private val validateEmailUseCase: ValidateEmailUseCase,
	private val sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase,
	private val activateEmailUseCase: ActivateEmailUseCase
) : ViewModel() {

	private val _confirmationButtonEnabledLiveData = MutableLiveData<Boolean>()
	private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
	private val _sendActivationCodeErrorLiveData = MutableLiveData<ErrorMessage>()
	private val _activateEmailSuccessLiveData = MutableLiveData<Unit>()
	private val _activateEmailErrorLiveData = MutableLiveData<ErrorMessage>()

	val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData
	val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
	val sendActivationCodeErrorLiveData: LiveData<ErrorMessage> = _sendActivationCodeErrorLiveData
	val activateEmailSuccessLiveData: LiveData<Unit> = _activateEmailSuccessLiveData
	val activateEmailErrorLiveData: LiveData<ErrorMessage> = _activateEmailErrorLiveData

	fun validateEmail(email: String) {
		validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
			it.fold(::handleValidateEmailFailure) { updateConfirmationStatus(true) }
		}
	}

	private fun handleValidateEmailFailure(failure: Failure) {
		if (failure is ValidateEmailError) {
			updateConfirmationStatus(false)
		}
	}

	private fun updateConfirmationStatus(enabled: Boolean) {
		_confirmationButtonEnabledLiveData.postValue(enabled)
	}

	fun sendActivationCode(email: String) {
		sendEmailActivationCodeUseCase(viewModelScope, SendEmailActivationCodeParams(email), Dispatchers.Default) {
			it.fold(::sendActivationCodeFailure) { sendActivationCodeSuccess() }
		}
	}

	private fun sendActivationCodeSuccess() {
		_sendActivationCodeSuccessLiveData.postValue(Unit)
	}

	private fun sendActivationCodeFailure(failure: Failure) {
		when (failure) {
			is EmailBlacklisted -> _sendActivationCodeErrorLiveData.value =
				ErrorMessage(R.string.create_personal_account_with_email_email_blacklisted_error)
			is EmailInUse -> _sendActivationCodeErrorLiveData.value =
				ErrorMessage(R.string.create_personal_account_with_email_email_in_use_error)
		}
	}

	fun activateEmail(email: String, code: String) {
		activateEmailUseCase(viewModelScope, ActivateEmailParams(email, code), Dispatchers.Default) {
			it.fold(::activateEmailFailure) { activateEmailSuccess() }
		}
	}

	private fun activateEmailSuccess() {
		_activateEmailSuccessLiveData.postValue(Unit)
	}

	private fun activateEmailFailure(failure: Failure) {
		when (failure) {
			is InvalidCode -> _activateEmailErrorLiveData.value =
				ErrorMessage(R.string.email_verification_invalid_code_error)
		}
	}
}

data class ErrorMessage(@StringRes val errorMessage: Int)
