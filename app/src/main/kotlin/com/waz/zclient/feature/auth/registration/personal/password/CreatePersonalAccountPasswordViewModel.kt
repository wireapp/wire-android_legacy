package com.waz.zclient.feature.auth.registration.personal.password

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.RegistrationParams
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.user.password.ValidatePasswordFailure
import com.waz.zclient.shared.user.password.ValidatePasswordParams
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountPasswordViewModel(
    private val validatePasswordCase: ValidatePasswordUseCase,
    private val passwordLengthConfig: PasswordLengthConfig,
    private val registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase
) : ViewModel() {

    private val _isValidPasswordLiveData = MutableLiveData<Boolean>()
    private val _registerSuccessLiveData = MutableLiveData<Unit>()
    private val _registerErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _networkConnectionErrorLiveData = MutableLiveData<Unit>()

    val isValidPasswordLiveData: LiveData<Boolean> = _isValidPasswordLiveData
    val registerSuccessLiveData: LiveData<Unit> = _registerSuccessLiveData
    val registerErrorLiveData: LiveData<ErrorMessage> = _registerErrorLiveData
    val networkConnectionErrorLiveData: LiveData<Unit> = _networkConnectionErrorLiveData

    fun validatePassword(password: String) {
        validatePasswordCase(viewModelScope, ValidatePasswordParams(
            password,
            passwordLengthConfig.minLength,
            passwordLengthConfig.maxLength
        ), Dispatchers.Default) {
            it.fold(::validatePasswordFailure) { validatePasswordSuccess() }
        }
    }

    private fun validatePasswordSuccess() {
        _isValidPasswordLiveData.value = true
    }

    private fun validatePasswordFailure(failure: Failure) {
        if (failure is ValidatePasswordFailure) {
            _isValidPasswordLiveData.value = false
        }
    }

    fun register(name: String, email: String, password: String, activationCode: String) {
        registerPersonalAccountWithEmailUseCase(
            viewModelScope,
            RegistrationParams(name, email, password, activationCode)
        ) {
            it.fold(::registerFailure) { registerSuccess() }
        }
    }

    private fun registerSuccess() {
        _registerSuccessLiveData.value = Unit
    }

    private fun registerFailure(failure: Failure) {
        if (!isNetworkConnectionFailure(failure)) {
            when (failure) {
                is UnauthorizedEmail -> _registerErrorLiveData.value =
                    ErrorMessage(R.string.create_personal_account_unauthorized_email_error)
                is InvalidActivationCode -> _registerErrorLiveData.value =
                    ErrorMessage(R.string.create_personal_account_invalid_activation_code_error)
                is EmailInUse -> _registerErrorLiveData.value =
                    ErrorMessage(R.string.create_personal_account_email_in_use_error)
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
