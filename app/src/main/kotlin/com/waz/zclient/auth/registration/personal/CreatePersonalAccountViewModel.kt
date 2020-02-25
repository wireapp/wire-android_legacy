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


    private var _confirmationButtonEnabledLiveData = MutableLiveData<Boolean>()

    val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData

    fun validateEmail(email: String) {
        validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
            it.fold(::handleFailure, ::handleSuccess)
        }
    }

    private fun handleSuccess(validatedEmail: String) {
        _confirmationButtonEnabledLiveData.postValue(true)
    }

    private fun handleFailure(failure: Failure) {
        if (failure is ValidateEmailError) {
            _confirmationButtonEnabledLiveData.postValue(false)
        }
    }
}
