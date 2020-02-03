package com.waz.zclient.settings.about.di

import com.waz.zclient.core.config.Config
import com.waz.zclient.settings.about.SettingsAboutViewModel
import com.waz.zclient.settings.about.UrlConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val settingsAboutModule: Module = module {
    viewModel { SettingsAboutViewModel(get(), get()) }
    factory { UrlConfig(Config.websiteUrl()) }
}
