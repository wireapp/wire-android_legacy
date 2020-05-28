package com.waz.zclient.feature.settings.account.deleteaccount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.usecase.DefaultUseCaseExecutor
import com.waz.zclient.core.usecase.UseCaseExecutor

class SettingsAccountDeleteAccountViewModel(
    private val deleteAccountUserCase: DeleteAccountUseCase
) : ViewModel(),
    UseCaseExecutor by DefaultUseCaseExecutor() {

    private val _deletionConfirmedLiveData = MutableLiveData<Unit>()

    val deletionConfirmedLiveData: LiveData<Unit> = _deletionConfirmedLiveData

    //TODO create an error scenario to notify the user when something has gone wrong?
    fun onDeleteAccountConfirmed() {
        deleteAccountUserCase(viewModelScope, Unit) {
            it.fold({}, { _deletionConfirmedLiveData.postValue(Unit) })
        }
    }
}
