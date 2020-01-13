package com.waz.zclient.settings.account.di

import com.waz.zclient.settings.account.SettingsAccountViewModel
import com.waz.zclient.settings.account.edithandle.EditHandleFragmentViewModel
import com.waz.zclient.user.domain.usecase.*
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

@ExperimentalCoroutinesApi
val settingsAccountModule: Module = module {
    viewModel { SettingsAccountViewModel(get(), get(), get(), get(), get()) }
    viewModel { EditHandleFragmentViewModel() }
    factory { GetUserProfileUseCase(get()) }
    factory { ChangeNameUseCase(get()) }
    factory { ChangePhoneUseCase(get()) }
    factory { ChangeHandleUseCase(get()) }
    factory { ChangeEmailUseCase(get()) }
}
