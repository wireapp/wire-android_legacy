package com.waz.zclient.feature.settings.devices.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.feature.settings.devices.ClientItem
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.usecase.GetClientUseCase
import com.waz.zclient.shared.clients.usecase.GetSpecificClientParams

class SettingsDeviceDetailViewModel(private val getClientByIdUseCase: GetClientUseCase) : ViewModel() {

    private val mutableLoading = MutableLiveData<Boolean>()
    private val mutableError = MutableLiveData<String>()
    private val mutableClient = MutableLiveData<ClientItem>()

    val loading: LiveData<Boolean>
        get() = mutableLoading

    val currentDevice: LiveData<ClientItem>
        get() = mutableClient

    val error: LiveData<String>
        get() = mutableError

    fun loadData(clientId: String) {
        handleLoading(true)
        getClientByIdUseCase(viewModelScope, GetSpecificClientParams(clientId)) { response ->
            response.fold(::handleGetDeviceError, ::handleGetDeviceSuccess)
        }
    }

    private fun handleGetDeviceError(failure: Failure) {
        handleLoading(false)
        Log.e(LOG_TAG, "Failure: $failure")
    }

    private fun handleGetDeviceSuccess(client: Client) {
        handleLoading(false)
        handleSuccess(client)
    }

    private fun handleSuccess(client: Client) {
        mutableClient.postValue(ClientItem(client))
    }

    private fun handleFailure(message: String) {
        mutableError.postValue(message)
    }

    private fun handleLoading(isLoading: Boolean) {
        mutableLoading.postValue(isLoading)
    }

    companion object {
        private const val LOG_TAG = "SettingsDeviceDetailVM"
    }
}
