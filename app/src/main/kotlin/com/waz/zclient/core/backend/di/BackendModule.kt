package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.datasources.BackendDataSource
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.datasources.remote.BackendApiService
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import org.koin.core.module.Module
import org.koin.dsl.module

val backendModule: Module = module {
    single { BackendDataSource(get(), get(), get(), get()) as BackendRepository }

    factory { BackendClient() }
    single { BackendRemoteDataSource(get()) }
    single { BackendRemoteDataSourceProvider() }
    factory { BackendApiService(get(), get()) }
    factory { BackendLocalDataSource(get()) }
    factory { BackendMapper() }
    factory { get<BackendRepository>().backendConfig() }
}
