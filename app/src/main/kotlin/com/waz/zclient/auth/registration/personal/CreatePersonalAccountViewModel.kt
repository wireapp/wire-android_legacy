package com.waz.zclient.auth.registration.personal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.email.*
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountViewModel(
    private val validateEmailUseCase: ValidateEmailUseCase
) : ViewModel() {


    private var _successLiveData = MutableLiveData<ValidateEmailSuccess>()
    private var _errorLiveData = MutableLiveData<ValidateEmailError>()

    val successLiveData: LiveData<ValidateEmailSuccess> = _successLiveData
    val errorLiveData: LiveData<ValidateEmailError> = _errorLiveData

    fun validateEmail(email: String) {
        validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
            it.fold(::handleFailure, ::handleSuccess)
        }
    }

    private fun handleSuccess(validatedEmail: String) {
        _successLiveData.postValue(EmailValid)
    }

    private fun handleFailure(failure: Failure) {
        if (failure is ValidateEmailError) {
            _errorLiveData.postValue(failure)
        }
    }
}
