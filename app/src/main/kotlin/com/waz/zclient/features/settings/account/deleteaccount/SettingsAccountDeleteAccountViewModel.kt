package com.waz.zclient.features.settings.account.deleteaccount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class SettingsAccountDeleteAccountViewModel(
    private val deleteAccountUserCase: DeleteAccountUseCase
) : ViewModel() {

    private val _deletionConfirmedLiveData = MutableLiveData<Unit>()

    val deletionConfirmedLiveData: LiveData<Unit> = _deletionConfirmedLiveData

    fun onDeleteAccountConfirmed() {
        deleteAccountUserCase(viewModelScope, Unit) {
            it.fold({}, { _deletionConfirmedLiveData.postValue(Unit) })
        }
    }
}
