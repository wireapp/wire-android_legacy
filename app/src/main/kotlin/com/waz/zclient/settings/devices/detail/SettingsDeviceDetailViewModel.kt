package com.waz.zclient.settings.devices.detail

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.devices.domain.GetClientUseCase
import com.waz.zclient.devices.domain.GetSpecificClientParams
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.devices.model.ClientItem

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
        when (failure) {
            is HttpError ->
                Log.e(javaClass.simpleName, "failed with errorCode: ${failure.errorCode} and errorMessage {${failure.errorMessage}")
            else ->
                Log.e(javaClass.simpleName, "Misc error scenario")
        }
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
