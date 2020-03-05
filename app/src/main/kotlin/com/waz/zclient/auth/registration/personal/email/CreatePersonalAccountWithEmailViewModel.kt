package com.waz.zclient.auth.registration.personal.email

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.auth.registration.activation.SendActivationCodeFailure
import com.waz.zclient.auth.registration.activation.SendActivationCodeSuccess
import com.waz.zclient.auth.registration.activation.SendEmailActivationCodeParams
import com.waz.zclient.auth.registration.activation.SendEmailActivationCodeUseCase
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.email.ValidateEmailError
import com.waz.zclient.user.domain.usecase.email.ValidateEmailParams
import com.waz.zclient.user.domain.usecase.email.ValidateEmailUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountWithEmailViewModel(
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase) : ViewModel() {

    private val _confirmationButtonEnabledLiveData = MutableLiveData<Boolean>()
    private val _sendActivationCodeSuccessLiveData = MutableLiveData<SendActivationCodeSuccess>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<SendActivationCodeFailure>()

    val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData
    val sendActivationCodeSuccessLiveData: LiveData<SendActivationCodeSuccess> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<SendActivationCodeFailure> = _sendActivationCodeErrorLiveData

    fun validateEmail(email: String) {
        validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
            it.fold(::handleValidateEmailFailure) { updateConfirmation(true) }
        }
    }

    private fun handleValidateEmailFailure(failure: Failure) {
        if (failure is ValidateEmailError) {
            updateConfirmation(false)
        }
    }

    private fun updateConfirmation(enabled: Boolean) {
        _confirmationButtonEnabledLiveData.postValue(enabled)
    }

    fun sendActivationCode(email: String) {
        sendEmailActivationCodeUseCase(viewModelScope, SendEmailActivationCodeParams(email), Dispatchers.Default) {
            it.fold(::sendActivationCodeFailure, ::sendActivationCodeSuccess)
        }
    }

    private fun sendActivationCodeSuccess(success: SendActivationCodeSuccess) {
        _sendActivationCodeSuccessLiveData.postValue(success)
    }

    private fun sendActivationCodeFailure(failure: Failure) {
        if (failure is SendActivationCodeFailure) {
            _sendActivationCodeErrorLiveData.postValue(failure)
        }
    }
}
