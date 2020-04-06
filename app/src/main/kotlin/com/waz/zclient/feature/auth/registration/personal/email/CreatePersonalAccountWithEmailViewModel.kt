package com.waz.zclient.feature.auth.registration.personal.email

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.shared.activation.usecase.EmailBlackListed
import com.waz.zclient.shared.activation.usecase.EmailInUse
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
    private val _sendActivationCodeErrorLiveData = MutableLiveData<EmailErrorMessage>()

    val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData
    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<EmailErrorMessage> = _sendActivationCodeErrorLiveData

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
        when (failure) {
            is EmailBlackListed -> _sendActivationCodeErrorLiveData.postValue(
                EmailErrorMessage(R.string.create_personal_account_with_email_email_blacklisted_error))
            is EmailInUse -> _sendActivationCodeErrorLiveData.postValue(
                EmailErrorMessage(R.string.create_personal_account_with_email_email_in_use_error))
        }
    }
}

data class EmailErrorMessage(@StringRes val errorMessage: Int)
