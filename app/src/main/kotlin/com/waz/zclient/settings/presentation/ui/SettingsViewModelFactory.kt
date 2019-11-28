package com.waz.zclient.settings.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.devices.data.ClientsRepositoryImpl
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetCurrentDeviceUseCase
import com.waz.zclient.settings.presentation.ui.account.SettingsAccountViewModel
import com.waz.zclient.settings.presentation.ui.devices.list.SettingsDeviceListViewModel

@Suppress("UNCHECKED_CAST")
class SettingsViewModelFactory : ViewModelProvider.Factory {

    private val getCurrentDeviceUseCase by lazy {
        GetCurrentDeviceUseCase(ClientsRepositoryImpl.getInstance())
    }

    private val getAllClientsUseCase by lazy {
        GetAllClientsUseCase(ClientsRepositoryImpl.getInstance())
    }

    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsAccountViewModel::class.java) -> {
                    createSettingsAccountViewModel() as T
                }
                isAssignableFrom(SettingsDeviceListViewModel::class.java) -> {
                    createSettingsDevicesViewModel() as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    private fun createSettingsDevicesViewModel() =
        SettingsDeviceListViewModel(getAllClientsUseCase, getCurrentDeviceUseCase)

    private fun createSettingsAccountViewModel() = SettingsAccountViewModel()
}
