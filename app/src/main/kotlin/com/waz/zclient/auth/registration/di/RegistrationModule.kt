package com.waz.zclient.auth.registration.di

import com.waz.zclient.auth.registration.activation.ActivationApi
import com.waz.zclient.auth.registration.activation.ActivationDataSource
import com.waz.zclient.auth.registration.activation.ActivationRemoteDataSource
import com.waz.zclient.auth.registration.activation.ActivationRepository
import com.waz.zclient.auth.registration.activation.SendEmailActivationCodeUseCase
import com.waz.zclient.auth.registration.personal.email.CreatePersonalAccountWithEmailViewModel
import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.user.domain.usecase.email.ValidateEmailUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val REGISTRATION_SCOPE_ID = "RegistrationScopeId"
const val REGISTRATION_SCOPE = "RegistrationScope"

val registrationModules: List<Module>
    get() = listOf(createPersonalAccountModule, activationModule)

val createPersonalAccountModule: Module = module {
    scope(named(REGISTRATION_SCOPE)) {
        viewModel { CreatePersonalAccountWithEmailViewModel(get(), get()) }
        factory { ValidateEmailUseCase() }
    }
}

val activationModule: Module = module {
    scope(named(REGISTRATION_SCOPE)) {
        factory { SendEmailActivationCodeUseCase(get()) }
        scoped { ActivationDataSource(get()) as ActivationRepository }
        factory { ActivationRemoteDataSource(get(), get()) }
        factory { get<NetworkClient>().create(ActivationApi::class.java) }
    }
}
