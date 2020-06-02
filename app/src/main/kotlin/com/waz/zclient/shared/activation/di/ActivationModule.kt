package com.waz.zclient.shared.activation.di

import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.shared.activation.ActivationRepository
import com.waz.zclient.shared.activation.datasources.ActivationDataSource
import com.waz.zclient.shared.activation.datasources.remote.ActivationApi
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.ActivatePhoneUseCase
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

val activationModule: Module = module {
    factory { SendEmailActivationCodeUseCase(get()) }
    factory { SendPhoneActivationCodeUseCase(get()) }
    factory { ActivateEmailUseCase(get()) }
    factory { ActivatePhoneUseCase(get()) }
    single { ActivationDataSource(get()) as ActivationRepository }
    factory { ActivationRemoteDataSource(get(), get()) }
    factory { get<NetworkClient>().create(ActivationApi::class.java) }
}
