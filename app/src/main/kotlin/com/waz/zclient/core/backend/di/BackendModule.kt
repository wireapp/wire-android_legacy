package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.storage.pref.backend.BackendPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

val backendModule: Module = module {
    factory { get<BackendClient>().get(get<BackendPreferences>().environment) }
    factory { BackendClient() }
}
