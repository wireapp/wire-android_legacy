package com.waz.zclient.feature.auth.registration.personal.phone

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
import com.waz.zclient.shared.activation.usecase.PhoneBlacklisted
import com.waz.zclient.shared.activation.usecase.PhoneInUse
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeParams
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberFailure
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberParams
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountPhoneViewModel(
    private val validatePhoneNumberUseCase: ValidatePhoneNumberUseCase,
    private val sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase
) : ViewModel(), UseCaseExecutor by DefaultUseCaseExecutor() {

    private val _isValidPhoneLiveData = MutableLiveData<Boolean>()
    private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _networkConnectionErrorLiveData = MutableLiveData<Unit>()

    val isValidPhoneLiveData: LiveData<Boolean> = _isValidPhoneLiveData
    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<ErrorMessage> = _sendActivationCodeErrorLiveData
    val networkConnectionErrorLiveData: LiveData<Unit> = _networkConnectionErrorLiveData


    fun validatePhone(countryCode: String, phoneNumber: String) {
        validatePhoneNumberUseCase(viewModelScope,
            ValidatePhoneNumberParams(countryCode, phoneNumber), Dispatchers.Default) {
            it.fold(::validatePhoneFailure) { validatePhoneSuccess() }
        }
    }

    private fun validatePhoneSuccess() {
        _isValidPhoneLiveData.value = true
    }

    private fun validatePhoneFailure(failure: Failure) {
        if (failure is ValidatePhoneNumberFailure) {
            _isValidPhoneLiveData.value = false
        }
    }

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
}

data class ErrorMessage(@StringRes val message: Int)
