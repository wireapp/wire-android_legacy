package com.waz.zclient.settings.account.di

import com.waz.zclient.settings.account.SettingsAccountViewModel
import com.waz.zclient.user.domain.usecase.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.ChangePhoneUseCase
import com.waz.zclient.user.domain.usecase.GetUserProfileUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module


val settingsAccountModule: Module = module {
    viewModel { SettingsAccountViewModel(get()) }
    factory { GetUserProfileUseCase(get()) }
    factory { ChangePhoneUseCase(get()) }
    factory { ChangeHandleUseCase(get()) }
}
