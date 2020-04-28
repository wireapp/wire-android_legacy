package com.waz.zclient.feature.auth.registration.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountWithEmailViewModel
import com.waz.zclient.feature.auth.registration.personal.email.EmailCredentialsViewModel
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.RegisterDataSource
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterApi
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val REGISTRATION_SCOPE_ID = "RegistrationScopeId"
const val REGISTRATION_SCOPE = "RegistrationScope"

val registrationModules: List<Module>
    get() = listOf(createPersonalAccountModule, registerModule)

val createPersonalAccountModule: Module = module {
    scope(named(REGISTRATION_SCOPE)) {
        viewModel { CreatePersonalAccountWithEmailViewModel(get(), get(), get(), get(), get(), get(), get()) }
        viewModel { EmailCredentialsViewModel() }
    }
}

val registerModule: Module = module {
    scope(named(REGISTRATION_SCOPE)) {
        factory { RegisterPersonalAccountWithEmailUseCase(get()) }
        scoped { RegisterDataSource(get()) as RegisterRepository }
        factory { RegisterRemoteDataSource(get(), get()) }
        factory { get<NetworkClient>().create(RegisterApi::class.java) }
    }
}
