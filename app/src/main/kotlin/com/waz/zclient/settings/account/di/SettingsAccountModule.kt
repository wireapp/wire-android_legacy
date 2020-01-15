package com.waz.zclient.settings.account.di

import com.waz.zclient.settings.account.SettingsAccountViewModel
import com.waz.zclient.user.domain.usecase.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val settingsAccountModule: Module = module {
    viewModel { SettingsAccountViewModel(get(), get(), get(), get(), get()) }
    factory { GetUserProfileUseCase(get()) }
    factory { ChangeNameUseCase(get()) }
    factory { ChangePhoneUseCase(get()) }
    factory { ChangeHandleUseCase(get()) }
    factory { ChangeEmailUseCase(get()) }
}
