package com.waz.zclient.auth.registration.di

import com.waz.zclient.auth.registration.activation.*
import com.waz.zclient.auth.registration.personal.email.CreatePersonalAccountWithEmailViewModel
import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.user.domain.usecase.email.ValidateEmailUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val registrationModules: List<Module>
    get() = listOf(createPersonalAccountModule, activationModule)

val createPersonalAccountModule: Module = module {
    viewModel { CreatePersonalAccountWithEmailViewModel(get(), get()) }
    factory { ValidateEmailUseCase() }
}

val activationModule: Module = module {
    factory { SendEmailActivationCodeUseCase(get()) }
    single { ActivationDataSource(get()) as ActivationRepository }
    factory { ActivationRemoteDataSource(get(), get()) }
    factory { get<NetworkClient>().create(ActivationApi::class.java) }
}
