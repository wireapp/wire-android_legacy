package com.waz.zclient.feature.auth.registration.personal.phone.name

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
import com.waz.zclient.feature.auth.registration.personal.email.password.ErrorMessage
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidPhoneActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.PhoneInUse
import com.waz.zclient.feature.auth.registration.register.usecase.PhoneRegistrationParams
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithPhoneUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedPhone
import com.waz.zclient.shared.user.name.ValidateNameFailure
import com.waz.zclient.shared.user.name.ValidateNameParams
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountPhoneNameViewModel(
    private val validateNameUseCase: ValidateNameUseCase,
    private val registerPersonalAccountWithPhoneUseCase: RegisterPersonalAccountWithPhoneUseCase
) : ViewModel(), UseCaseExecutor by DefaultUseCaseExecutor() {

    private val _isValidNameLiveData = MutableLiveData<Boolean>()
    private val _registerSuccessLiveData = MutableLiveData<Unit>()
    private val _registerErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _networkConnectionErrorLiveData = MutableLiveData<Unit>()

    val isValidNameLiveData: LiveData<Boolean> = _isValidNameLiveData
    val registerSuccessLiveData: LiveData<Unit> = _registerSuccessLiveData
    val registerErrorLiveData: LiveData<ErrorMessage> = _registerErrorLiveData
    val networkConnectionErrorLiveData: LiveData<Unit> = _networkConnectionErrorLiveData

    fun validateName(name: String) {
        validateNameUseCase(viewModelScope, ValidateNameParams(name), Dispatchers.Default) {
            it.fold(::validateNameFailure) { validateNameSuccess() }
        }
    }

    private fun validateNameSuccess() {
        _isValidNameLiveData.value = true
    }

    private fun validateNameFailure(failure: Failure) {
        if (failure is ValidateNameFailure) {
            _isValidNameLiveData.value = false
        }
    }

    fun register(name: String, phone: String, activationCode: String) {
        registerPersonalAccountWithPhoneUseCase(
            viewModelScope,
            PhoneRegistrationParams(name, phone, activationCode)
        ) {
            it.fold(::registerFailure) { registerSuccess() }
        }
    }

    private fun registerSuccess() {
        _registerSuccessLiveData.value = Unit
    }

    private fun registerFailure(failure: Failure) {
        when (failure) {
            is NetworkConnection -> _networkConnectionErrorLiveData.value = Unit
            is UnauthorizedPhone -> _registerErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_unauthorized_phone_error)
            is InvalidPhoneActivationCode -> _registerErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_invalid_activation_code_error)
            is PhoneInUse -> _registerErrorLiveData.value =
                ErrorMessage(R.string.create_personal_account_phone_in_use_error)
        }
    }
}

data class ErrorMessage(@StringRes val message: Int)
