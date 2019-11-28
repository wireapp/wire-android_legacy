package com.waz.zclient.settings.presentation.ui.devices.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.data.source.remote.Failure
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.presentation.mapper.toPresentationList
import com.waz.zclient.settings.presentation.ui.devices.model.ClientItem

sealed class ClientPresentationState {
    object Loading : ClientPresentationState()
    object Empty : ClientPresentationState()
    data class Error(val failure: Failure) : ClientPresentationState()
}

class SettingsDeviceListViewModel(private val getAllClientsUseCase: GetAllClientsUseCase,
                                  private val getCurrentDeviceUseCase: GetCurrentDeviceUseCase) : ViewModel() {

    val state: LiveData<ClientPresentationState>
        get() = mutableState

    private val mutableState = MutableLiveData<ClientPresentationState>().apply {
        this.value = ClientPresentationState.Loading
    }

    val otherDevices: LiveData<List<ClientItem>>
        get() = mutableOtherDevices

    private val mutableOtherDevices = MutableLiveData<List<ClientItem>>()

    val currentDevice: LiveData<ClientItem>
        get() = mutableCurrentDevice

    private val mutableCurrentDevice = MutableLiveData<ClientItem>()

    fun loadData() {
        getAllClientsUseCase.invoke(viewModelScope, Unit) {
            it.either(::handleFailure, ::handleAllClientsSuccess)
        }
    }

    private fun handleFailure(failure: Failure) {
        mutableState.value = ClientPresentationState.Error(failure)
    }

    private fun handleAllClientsSuccess(result: RequestResult<List<Client>>) {
        when {
            result.data.isNullOrEmpty() -> mutableState.value = ClientPresentationState.Empty
            result.data.isNotEmpty() -> mutableOtherDevices.value = result.data.toPresentationList()
        }
    }
}
