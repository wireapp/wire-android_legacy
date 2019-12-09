package com.waz.zclient.settings.presentation.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.devices.data.ClientsRepository
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetSpecificClientUseCase
import com.waz.zclient.settings.presentation.ui.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.settings.presentation.ui.devices.list.SettingsDeviceListViewModel

@Suppress("UNCHECKED_CAST")
class SettingsDeviceViewModelFactory : ViewModelProvider.Factory {

    private val getCurrentDeviceUseCase by lazy {
        GetSpecificClientUseCase(ClientsRepository.getInstance())
    }

    private val getAllClientsUseCase by lazy {
        GetAllClientsUseCase(ClientsRepository.getInstance())
    }

    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsDeviceListViewModel::class.java) -> {
                    createSettingsDevicesViewModel() as T
                }
                isAssignableFrom(SettingsDeviceDetailViewModel::class.java) -> {
                    createSettingsDeviceDetailViewModel() as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    private fun createSettingsDeviceDetailViewModel() =
        SettingsDeviceDetailViewModel(getCurrentDeviceUseCase)

    private fun createSettingsDevicesViewModel() =
        SettingsDeviceListViewModel(getAllClientsUseCase, getCurrentDeviceUseCase)
}
