package com.waz.zclient.auth.registration.di

import com.waz.zclient.auth.registration.personal.CreatePersonalAccountViewModel
import com.waz.zclient.user.domain.usecase.email.ValidateEmailUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val registrationModules: List<Module>
    get() = listOf(registrationModule)

val registrationModule: Module = module {
    viewModel { CreatePersonalAccountViewModel(get()) }
    factory { ValidateEmailUseCase() }
}
