package com.waz.zclient.feature.auth.registration.personal.pincode

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.shared.activation.usecase.ActivateEmailParams
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeParams
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase

class CreatePersonalAccountPinCodeViewModel(
    private val sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase,
    private val activateEmailUseCase: ActivateEmailUseCase
) : ViewModel() {

    private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _activateEmailSuccessLiveData = MutableLiveData<Unit>()
    private val _activateEmailErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _networkConnectionErrorLiveData = MutableLiveData<Unit>()

    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<ErrorMessage> = _sendActivationCodeErrorLiveData
    val activateEmailSuccessLiveData: LiveData<Unit> = _activateEmailSuccessLiveData
    val activateEmailErrorLiveData: LiveData<ErrorMessage> = _activateEmailErrorLiveData
    val networkConnectionErrorLiveData: LiveData<Unit> = _networkConnectionErrorLiveData

    fun sendActivationCode(email: String) {
        sendEmailActivationCodeUseCase(viewModelScope, SendEmailActivationCodeParams(email)) {
            it.fold(::sendActivationCodeFailure) { sendActivationCodeSuccess() }
        }
    }

    private fun sendActivationCodeSuccess() {
        _sendActivationCodeSuccessLiveData.value = Unit
    }

    private fun sendActivationCodeFailure(failure: Failure) {
        if (!isNetworkConnectionFailure(failure)) {
            when (failure) {
                is EmailBlacklisted -> _sendActivationCodeErrorLiveData.value =
                    ErrorMessage(R.string.create_personal_account_with_email_email_blacklisted_error)
                is EmailInUse -> _sendActivationCodeErrorLiveData.value =
                    ErrorMessage(R.string.create_personal_account_with_email_email_in_use_error)
            }
        }
    }

    fun activateEmail(email: String, code: String) {
        activateEmailUseCase(viewModelScope, ActivateEmailParams(email, code)) {
            it.fold(::activateEmailFailure) { activateEmailSuccess() }
        }
    }

    private fun activateEmailSuccess() {
        _activateEmailSuccessLiveData.value = Unit
    }

    private fun activateEmailFailure(failure: Failure) {
        if (!isNetworkConnectionFailure(failure)) {
            when (failure) {
                is InvalidCode -> _activateEmailErrorLiveData.value =
                    ErrorMessage(R.string.email_verification_invalid_code_error)
            }
        }
    }

    private fun isNetworkConnectionFailure(failure: Failure): Boolean {
        return if (failure is NetworkConnection) {
            _networkConnectionErrorLiveData.value = Unit
            true
        } else false
    }
}

data class ErrorMessage(@StringRes val message: Int)
