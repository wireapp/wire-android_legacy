package com.waz.zclient.settings.support.di

import com.waz.zclient.settings.support.SettingsSupportViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val settingsSupportModule: Module = module {
    viewModel { SettingsSupportViewModel() }
}
