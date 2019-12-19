package com.waz.zclient.settings.devices.di

import com.waz.zclient.devices.domain.GetAllClientsUseCase
import com.waz.zclient.devices.domain.GetClientUseCase
import com.waz.zclient.settings.devices.detail.SettingsDeviceDetailViewModel
import com.waz.zclient.settings.devices.list.SettingsDeviceListViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val settingsDeviceModule: Module = module {
    viewModel { SettingsDeviceListViewModel(get()) }
    viewModel { SettingsDeviceDetailViewModel(get()) }
    factory { GetAllClientsUseCase(get()) }
    factory { GetClientUseCase(get()) }
}
