package com.waz.zclient.settings.presentation.ui.devices

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fernandocejas.sample.core.functional.Failure
import com.waz.zclient.core.data.source.remote.RequestResult
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
import com.waz.zclient.devices.model.ClientEntity
import com.waz.zclient.settings.presentation.ui.devices.model.ClientsUiModel
import com.waz.zclient.settings.user.usecase.GetUserProfileUseCase

class SettingsDevicesViewModel(private val getUserProfileUseCase: GetUserProfileUseCase,
                               private val getAllClientsUseCase: GetAllClientsUseCase,
                               private val getCurrentDeviceUseCase: GetCurrentDeviceUseCase) : ViewModel() {

    sealed class ClientsState {
        object Loading : ClientsState()
        object Empty : ClientsState()
        data class Failed(val failure: Failure) : ClientsState()
        data class Success(val clients: List<ClientsUiModel>) : ClientsState()
    }

    val state = MutableLiveData<ClientsState>().apply {
        this.value = ClientsState.Loading
    }

    fun loadData() {
        getAllClientsUseCase.invoke(viewModelScope, Unit) {
            it.either(::handleFailure, ::handleSuccess)
        }
    }

    private fun handleFailure(failure: Failure) {
        state.value = ClientsState.Failed(failure)
    }

    private fun handleSuccess(result: RequestResult<Array<ClientEntity>>) {
        when {
            result.data.isNullOrEmpty() -> state.value = ClientsState.Empty
            result.data.isNotEmpty() -> state.value = ClientsState.Success(mapToPresentation(result.data.toList()))
        }
    }

    private fun mapToPresentation(friends: List<ClientEntity>): List<ClientsUiModel> {
        return friends.map { ClientsUiModel(it.time, it.label, it.id) }
    }
}
