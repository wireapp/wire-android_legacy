package com.waz.zclient.settings.devices.list

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetSpecificClientUseCase
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.devices.model.ClientItem

class SettingsDeviceListViewModel(private val getAllClientsUseCase: GetAllClientsUseCase,
                                  private val getSpecificClientUseCase: GetSpecificClientUseCase) : ViewModel() {

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
        when (failure) {
            is Failure.CancellationError ->
                // Show error for cancellation error
                Log.e(javaClass.simpleName, "The request for data was cancelled")
            else -> //Show error for soemthing else
                Log.e(javaClass.simpleName, "Misc error scenario")
        }
    }

    private fun handleAllDevicesSuccess(clients: List<Client>) {
        handleLoading(false)
        handleAllClientsSuccess(clients)
    }

    private fun handleLoading(isLoading: Boolean) {
        mutableLoading.postValue(isLoading)
    }

    private fun handleAllClientsSuccess(result: List<Client>) {
        when {
            result.isNullOrEmpty() -> mutableOtherDevices.value = listOf()
            result.isNotEmpty() -> mutableOtherDevices.postValue(result.map {
                ClientItem(it)
            })
        }
    }

    private fun handleFailure(message: String) {
        mutableError.value = message
    }
}
