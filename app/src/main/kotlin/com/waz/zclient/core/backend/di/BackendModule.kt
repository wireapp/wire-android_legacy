package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.datasources.BackendDataSource
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefEndpoints
import com.waz.zclient.core.backend.datasources.local.CustomBackendPreferences
import com.waz.zclient.core.backend.datasources.remote.BackendApiService
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.items.BackendClient
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
    factory {
        val backendPreferences = get<BackendPreferences>()
        CustomBackendPreferences(
            backendPreferences.environment,
            CustomBackendPrefEndpoints(
                backendPreferences.baseUrl,
                backendPreferences.blacklistUrl,
                backendPreferences.teamsUrl,
                backendPreferences.accountsUrl,
                backendPreferences.websiteUrl
            )
        )
    }
    factory { BackendLocalDataSource(get(), get()) }
    factory { BackendMapper() }
}
