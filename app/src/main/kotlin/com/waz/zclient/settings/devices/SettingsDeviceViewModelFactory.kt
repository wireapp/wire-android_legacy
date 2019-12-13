package com.waz.zclient.settings.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waz.zclient.ContextProvider
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsNetwork
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetSpecificClientUseCase
import com.waz.zclient.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.settings.devices.list.SettingsDeviceListViewModel
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.pref.GlobalPreferences

@Suppress("UNCHECKED_CAST")
class SettingsDeviceViewModelFactory : ViewModelProvider.Factory {

    private val clientsRepository by lazy {
        val globalPreferences = GlobalPreferences(ContextProvider.getApplicationContext())
        val userId = globalPreferences.activeUserId
        val userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), userId)
        val clientApi = ClientsNetwork().getClientsApi()
        ClientsDataSource.getInstance(ClientsRemoteDataSource(clientApi),
            ClientsLocalDataSource(userDatabase.clientsDao()))
    }

    private val getCurrentDeviceUseCase by lazy {
        GetSpecificClientUseCase(clientsRepository)
    }

    private val getAllClientsUseCase by lazy {
        GetAllClientsUseCase(clientsRepository)
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
