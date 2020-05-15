package com.waz.zclient.feature.settings.devices.list

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.feature.settings.devices.ClientItem
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.usecase.GetAllClientsUseCase

class SettingsDeviceListViewModel(private val getAllClientsUseCase: GetAllClientsUseCase) : ViewModel() {

    private val mutableLoading = MutableLiveData<Boolean>()
    private val mutableError = MutableLiveData<String>()
    private val mutableOtherDevices = MutableLiveData<List<ClientItem>>()
    private val mutableCurrentDevice = MutableLiveData<ClientItem>()

    val loading: LiveData<Boolean>
        get() = mutableLoading

    val otherDevices: LiveData<List<ClientItem>>
        get() = mutableOtherDevices

    val currentDevice: LiveData<ClientItem>
        get() = mutableCurrentDevice

    val error: LiveData<String>
        get() = mutableError

    fun loadData() {
        handleLoading(true)
        getAllClientsUseCase.invoke(viewModelScope, Unit) { response ->
            response.fold(::handleAllDevicesError, ::handleAllDevicesSuccess)
        }
    }

    private fun handleAllDevicesError(failure: Failure) {
        handleLoading(false)
        Log.e(LOG_TAG, "Failure: $failure")
    }

    private fun handleAllDevicesSuccess(clients: List<Client>) {
        handleLoading(false)
        handleAllClientsSuccess(clients)
    }

    private fun handleLoading(isLoading: Boolean) {
        mutableLoading.postValue(isLoading)
    }

    private fun handleAllClientsSuccess(result: List<Client>) {
        mutableOtherDevices.postValue(result.map { ClientItem(it) })
    }

    private fun handleFailure(message: String) {
        mutableError.value = message
    }

    companion object {
        private const val LOG_TAG = "SettingsDeviceListVM"
    }
}
