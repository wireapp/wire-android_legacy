package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.core.backend.BackendDataSource
import com.waz.zclient.core.backend.datasources.BackendRepository
import com.waz.zclient.core.backend.datasources.local.BackendPrefsDataSource
import com.waz.zclient.core.backend.datasources.remote.BackendApiService
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.storage.pref.backend.BackendPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

val backendModule: Module = module {
    single { BackendDataSource(get(), get(), get()) as BackendRepository }

    factory { get<BackendClient>().get(get<BackendPreferences>().environment) }
    factory { BackendClient() }
    factory { BackendRemoteDataSource(get()) }
    factory { BackendApiService(get(), get()) }
    factory { BackendPrefsDataSource(get()) }
    factory { BackendMapper() }
}
