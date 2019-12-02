package com.waz.zclient.settings.presentation.ui.devices.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.core.resources.ResourceStatus
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.presentation.mapper.toPresentationList
import com.waz.zclient.settings.presentation.ui.devices.model.ClientItem

class SettingsDeviceListViewModel(private val getAllClientsUseCase: GetAllClientsUseCase,
                                  private val getCurrentDeviceUseCase: GetCurrentDeviceUseCase) : ViewModel() {

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
        getAllClientsUseCase.invoke(viewModelScope, Unit) {
            checkAllDevicesResult(it)
        }
    }

    private fun checkAllDevicesResult(result: Resource<List<Client>>) {
        when {
            result.status == ResourceStatus.SUCCESS -> result.data?.let {
                handleLoading(false)
                handleAllClientsSuccess(it)
            }
            result.status == ResourceStatus.ERROR -> result.message?.let {
                handleLoading(false)
                handleFailure(it)
            }
            result.status == ResourceStatus.LOADING -> handleLoading(true)
        }
    }

    private fun handleLoading(isLoading: Boolean) {
        mutableLoading.postValue(isLoading)
    }

    private fun handleAllClientsSuccess(result: List<Client>) {
        when {
            result.isNullOrEmpty() -> mutableOtherDevices.value = listOf()
            result.isNotEmpty() -> mutableOtherDevices.postValue(result.toPresentationList())
        }
    }

    private fun handleFailure(message: String) {
        mutableError.value = message
    }
}
