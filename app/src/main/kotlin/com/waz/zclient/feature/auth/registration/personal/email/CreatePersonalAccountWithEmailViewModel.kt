package com.waz.zclient.feature.auth.registration.personal.email

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.R
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.RegistrationParams
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
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
import com.waz.zclient.shared.user.name.ValidateNameFailure
import com.waz.zclient.shared.user.name.ValidateNameParams
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import com.waz.zclient.shared.user.password.ValidatePasswordFailure
import com.waz.zclient.shared.user.password.ValidatePasswordParams
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountWithEmailViewModel(
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase,
    private val activateEmailUseCase: ActivateEmailUseCase,
    private val validateNameUseCase: ValidateNameUseCase,
    private val validatePasswordCase: ValidatePasswordUseCase,
    private val passwordLengthConfig: PasswordLengthConfig,
    private val registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase
) : ViewModel() {

    private val _isValidEmailLiveData = MutableLiveData<Boolean>()
    private val _sendActivationCodeSuccessLiveData = MutableLiveData<Unit>()
    private val _sendActivationCodeErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _activateEmailSuccessLiveData = MutableLiveData<Unit>()
    private val _activateEmailErrorLiveData = MutableLiveData<ErrorMessage>()
    private val _isValidNameLiveData = MutableLiveData<Boolean>()
    private val _isValidPasswordLiveData = MutableLiveData<Boolean>()
    private val _registerSuccessLiveData = MutableLiveData<Unit>()
    private val _registerErrorLiveData = MutableLiveData<ErrorMessage>()

    val isValidEmailLiveData: LiveData<Boolean> = _isValidEmailLiveData
    val sendActivationCodeSuccessLiveData: LiveData<Unit> = _sendActivationCodeSuccessLiveData
    val sendActivationCodeErrorLiveData: LiveData<ErrorMessage> = _sendActivationCodeErrorLiveData
    val activateEmailSuccessLiveData: LiveData<Unit> = _activateEmailSuccessLiveData
    val activateEmailErrorLiveData: LiveData<ErrorMessage> = _activateEmailErrorLiveData
    val isValidNameLiveData: LiveData<Boolean> = _isValidNameLiveData
    val isValidPasswordLiveData: LiveData<Boolean> = _isValidPasswordLiveData
    val registerSuccessLiveData: LiveData<Unit> = _registerSuccessLiveData
    val registerErrorLiveData: LiveData<ErrorMessage> = _registerErrorLiveData

    fun validateEmail(email: String) {
        validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
            it.fold(::handleValidateEmailFailure) { updateEmailValidationStatus(true) }
        }
    }

    private fun handleValidateEmailFailure(failure: Failure) {
        if (failure is ValidateEmailError) {
            updateEmailValidationStatus(false)
        }
    }

    private fun updateEmailValidationStatus(enabled: Boolean) {
        _isValidEmailLiveData.postValue(enabled)
    }

    fun sendActivationCode(email: String) {
        sendEmailActivationCodeUseCase(viewModelScope, SendEmailActivationCodeParams(email)) {
            it.fold(::sendActivationCodeFailure) { _sendActivationCodeSuccessLiveData.postValue(Unit) }
        }
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
        activateEmailUseCase(viewModelScope, ActivateEmailParams(email, code)) {
            it.fold(::activateEmailFailure) { _activateEmailSuccessLiveData.postValue(Unit) }
        }
    }

    private fun activateEmailFailure(failure: Failure) {
        when (failure) {
            is InvalidCode -> _activateEmailErrorLiveData.value =
                ErrorMessage(R.string.email_verification_invalid_code_error)
        }
    }

    fun validateName(name: String) {
        validateNameUseCase(viewModelScope, ValidateNameParams(name), Dispatchers.Default) {
            it.fold(::handleValidateNameFailure) { _isValidNameLiveData.postValue(true) }
        }
    }

    private fun handleValidateNameFailure(failure: Failure) {
        if (failure is ValidateNameFailure) {
            _isValidNameLiveData.postValue(false)
        }
    }

    fun validatePassword(password: String) {
        validatePasswordCase(viewModelScope, ValidatePasswordParams(
            password,
            passwordLengthConfig.minLength,
            passwordLengthConfig.maxLength
        ), Dispatchers.Default) {
            it.fold(::handleValidatePasswordFailure) { _isValidPasswordLiveData.postValue(true) }
        }
    }

    private fun handleValidatePasswordFailure(failure: Failure) {
        if (failure is ValidatePasswordFailure) {
            _isValidPasswordLiveData.postValue(false)
        }
    }

    fun register(name: String, email: String, password: String, activationCode: String) {
        registerPersonalAccountWithEmailUseCase(
            viewModelScope,
            RegistrationParams(name, email, password, activationCode)
        ) {
            it.fold(::registerFailure) { _registerSuccessLiveData.postValue(Unit) }
        }
    }

    private fun registerFailure(failure: Failure) {
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

data class ErrorMessage(@StringRes val errorMessage: Int)
