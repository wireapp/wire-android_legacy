package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.Backend
import com.waz.zclient.core.backend.BackendConfig
import org.koin.core.module.Module
import org.koin.dsl.module

val backendModule: Module = module {
    single { BackendConfig().currentBackend() as Backend }
}
