package com.waz.zclient.settings.ui.devices.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.domain.GetSpecificClientUseCase
import com.waz.zclient.devices.domain.Params
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.ui.devices.model.ClientItem

class SettingsDeviceDetailViewModel(private val getClientByIdUseCase: GetSpecificClientUseCase) : ViewModel() {

    private val mutableLoading = MutableLiveData<Boolean>()
    private val mutableError = MutableLiveData<String>()
    private val mutableClient = MutableLiveData<ClientItem>()

    val loading: LiveData<Boolean>
        get() = mutableLoading

    val currentDevice: LiveData<ClientItem>
        get() = mutableClient

    val error: LiveData<String>
        get() = mutableError

    fun loadData(clientId: String?) {
        handleLoading(true)
        getClientByIdUseCase(viewModelScope, Params(clientId)) { response ->
            response.either(::handleGetDeviceError, ::handleGetDeviceSuccess)
        }
    }

    private fun handleGetDeviceError(failure: Failure) {
        handleLoading(false)
        handleFailure(failure.message)
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

}
