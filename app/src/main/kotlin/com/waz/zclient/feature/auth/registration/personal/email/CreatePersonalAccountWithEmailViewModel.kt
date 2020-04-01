package com.waz.zclient.feature.auth.registration.personal.email

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.shared.activation.usecase.SendActivationCodeFailure
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeParams
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.ValidateEmailError
import com.waz.zclient.shared.user.email.ValidateEmailParams
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountWithEmailViewModel(
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase
) : ViewModel() {

    private val _confirmationButtonEnabledLiveData = MutableLiveData<Boolean>()
    private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<SendActivationCodeFailure>()

    val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData
    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
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
            it.fold(::sendActivationCodeFailure) { sendActivationCodeSuccess() }
        }
    }

    private fun sendActivationCodeSuccess() {
        _sendActivationCodeSuccessLiveData.postValue(Unit)
    }

    private fun sendActivationCodeFailure(failure: Failure) {
        if (failure is SendActivationCodeFailure) {
            _sendActivationCodeErrorLiveData.postValue(failure)
        }
    }
}
