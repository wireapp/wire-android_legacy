package com.waz.zclient.feature.auth.registration.personal.phone.code

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.usecase.DefaultUseCaseExecutor
import com.waz.zclient.core.usecase.UseCaseExecutor
import com.waz.zclient.shared.activation.usecase.ActivatePhoneParams
import com.waz.zclient.shared.activation.usecase.ActivatePhoneUseCase
import com.waz.zclient.shared.activation.usecase.InvalidPhoneCode
import com.waz.zclient.shared.activation.usecase.PhoneBlacklisted
import com.waz.zclient.shared.activation.usecase.PhoneInUse
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeParams
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase

class CreatePersonalAccountPhoneCodeViewModel(
    private val sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase,
    private val activatePhoneUseCase: ActivatePhoneUseCase
) : ViewModel(), UseCaseExecutor by DefaultUseCaseExecutor() {

    private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _activatePhoneSuccessLiveData = MutableLiveData<Unit>()
    private val _activatePhoneErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _networkConnectionErrorLiveData = MutableLiveData<Unit>()

    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<ErrorMessage> = _sendActivationCodeErrorLiveData
    val activatePhoneSuccessLiveData: LiveData<Unit> = _activatePhoneSuccessLiveData
    val activatePhoneErrorLiveData: LiveData<ErrorMessage> = _activatePhoneErrorLiveData
    val networkConnectionErrorLiveData: LiveData<Unit> = _networkConnectionErrorLiveData

    fun sendActivationCode(phone: String) {
        sendPhoneActivationCodeUseCase(viewModelScope, SendPhoneActivationCodeParams(phone)) {
            it.fold(::sendActivationCodeFailure) { sendActivationCodeSuccess() }
        }
    }

    private fun sendActivationCodeSuccess() {
        _sendActivationCodeSuccessLiveData.value = Unit
    }

    private fun sendActivationCodeFailure(failure: Failure) {
        when (failure) {
            is NetworkConnection -> _networkConnectionErrorLiveData.value = Unit
            is PhoneBlacklisted -> _sendActivationCodeErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_with_phone_phone_blacklisted_error)
            is PhoneInUse -> _sendActivationCodeErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_with_phone_phone_in_use_error)
        }
    }

    fun activatePhone(phone: String, code: String) {
        activatePhoneUseCase(viewModelScope, ActivatePhoneParams(phone, code)) {
            it.fold(::activatePhoneFailure) { activatePhoneSuccess() }
        }
    }

    private fun activatePhoneSuccess() {
        _activatePhoneSuccessLiveData.value = Unit
    }

    private fun activatePhoneFailure(failure: Failure) {
        when (failure) {
            is NetworkConnection -> _networkConnectionErrorLiveData.value = Unit
            is InvalidPhoneCode -> _activatePhoneErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_phone_code_invalid_code_error)
        }
    }
}

data class ErrorMessage(@StringRes val message: Int)
