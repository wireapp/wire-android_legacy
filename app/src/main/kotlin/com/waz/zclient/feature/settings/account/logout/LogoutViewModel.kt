package com.waz.zclient.feature.settings.account.logout

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure

class LogoutViewModel(private val logoutUseCase: LogoutUseCase) : ViewModel() {

    private var _successLiveData = MutableLiveData<LogoutStatus>()
    private var _errorLiveData = MutableLiveData<Failure>()

    val errorLiveData: LiveData<Failure> = _errorLiveData
    val successLiveData: LiveData<LogoutStatus> = _successLiveData

    fun onVerifyButtonClicked() = logoutUseCase(viewModelScope, Unit) {
        it.fold(::logoutFailed, ::logoutSuccess)
    }

    private fun logoutSuccess(status: LogoutStatus) = _successLiveData.postValue(status)

    private fun logoutFailed(failure: Failure) = _errorLiveData.postValue(failure)
}
