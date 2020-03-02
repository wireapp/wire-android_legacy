package com.waz.zclient.features.auth.registration.personal

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.usecase.email.ValidateEmailError
import com.waz.zclient.user.usecase.email.ValidateEmailParams
import com.waz.zclient.user.usecase.email.ValidateEmailUseCase
import kotlinx.coroutines.Dispatchers

class CreatePersonalAccountViewModel(private val validateEmailUseCase: ValidateEmailUseCase) : ViewModel() {

    private val _confirmationButtonEnabledLiveData = MutableLiveData<Boolean>()

    val confirmationButtonEnabledLiveData: LiveData<Boolean> = _confirmationButtonEnabledLiveData

    fun validateEmail(email: String) {
        validateEmailUseCase(viewModelScope, ValidateEmailParams(email), Dispatchers.Default) {
            it.fold(::handleFailure) { updateConfirmation(true) }
        }
    }

    private fun handleFailure(failure: Failure) {
        if (failure is ValidateEmailError) {
            updateConfirmation(false)
        }
    }

    private fun updateConfirmation(enabled: Boolean) {
        _confirmationButtonEnabledLiveData.postValue(enabled)
    }
}
