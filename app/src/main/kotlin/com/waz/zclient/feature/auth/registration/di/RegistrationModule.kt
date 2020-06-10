package com.waz.zclient.feature.auth.registration.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailViewModel
import com.waz.zclient.feature.auth.registration.personal.email.code.CreatePersonalAccountEmailCodeViewModel
import com.waz.zclient.feature.auth.registration.personal.email.name.CreatePersonalAccountEmailNameViewModel
import com.waz.zclient.feature.auth.registration.personal.email.password.CreatePersonalAccountPasswordViewModel
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneCredentialsViewModel
import com.waz.zclient.feature.auth.registration.personal.phone.CreatePersonalAccountPhoneViewModel
import com.waz.zclient.feature.auth.registration.personal.phone.code.CreatePersonalAccountPhoneCodeViewModel
import com.waz.zclient.feature.auth.registration.personal.phone.name.CreatePersonalAccountPhoneNameViewModel
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.RegisterDataSource
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterApi
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithPhoneUseCase
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
        viewModel { CreatePersonalAccountEmailViewModel(get(), get()) }
        viewModel { CreatePersonalAccountEmailCodeViewModel(get(), get()) }
        viewModel { CreatePersonalAccountEmailNameViewModel(get()) }
        viewModel { CreatePersonalAccountPasswordViewModel(get(), get(), get()) }
        viewModel { CreatePersonalAccountEmailCredentialsViewModel() }
        viewModel { CreatePersonalAccountPhoneViewModel(get(), get()) }
        viewModel { CreatePersonalAccountPhoneCodeViewModel(get(), get()) }
        viewModel { CreatePersonalAccountPhoneNameViewModel(get(), get()) }
        viewModel { CreatePersonalAccountPhoneCredentialsViewModel() }
    }
}

val registerModule: Module = module {
    scope(named(REGISTRATION_SCOPE)) {
        factory { RegisterPersonalAccountWithEmailUseCase(get()) }
        factory { RegisterPersonalAccountWithPhoneUseCase(get()) }
        scoped { RegisterDataSource(get()) as RegisterRepository }
        factory { RegisterRemoteDataSource(get(), get()) }
        factory { get<NetworkClient>().create(RegisterApi::class.java) }
    }
}
