package com.waz.zclient.settings.account.logout

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure

class LogoutDialogViewModel(private val logoutUseCase: LogoutUseCase) : ViewModel() {

    private var _logoutLiveData = MutableLiveData<Boolean>()
    val logoutLiveData: LiveData<Boolean> = _logoutLiveData

    fun onVerifyButtonClicked() {
        logoutUseCase(viewModelScope, Unit) {
            it.fold(::logoutFailed, ::logoutSuccess)
        }
    }

    private fun logoutSuccess(unit: Unit) {
        _logoutLiveData.postValue(true)
    }

    private fun logoutFailed(failure: Failure) {

    }

}
