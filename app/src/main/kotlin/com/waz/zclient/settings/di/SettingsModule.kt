package com.waz.zclient.settings.di

import com.waz.zclient.settings.about.SettingsAboutViewModel
import com.waz.zclient.settings.support.SettingsSupportViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val settingsMainModule: Module = module {
    viewModel { SettingsAboutViewModel(get(), get(), get()) }
    viewModel { SettingsSupportViewModel() }
}
