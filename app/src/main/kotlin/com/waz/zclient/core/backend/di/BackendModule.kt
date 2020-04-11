package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.datasources.BackendDataSource
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.datasources.remote.BackendApiService
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

object BackendModule {
    //TODO: we won't need this once whole app uses BackendRepository for setting up custom backends
    @JvmStatic
    val backendConfigScopeManager = BackendConfigScopeManager()
}

val backendModule: Module = module {
    single { BackendDataSource(get(), get(), get(), get(), get()) as BackendRepository }

    factory { BackendClient() }
    single { BackendRemoteDataSource(get()) }
    single { BackendRemoteDataSourceProvider() }
    factory { BackendApiService(get(), get()) }
    factory { BackendLocalDataSource(get()) }
    factory { BackendMapper() }

    single { BackendModule.backendConfigScopeManager }
    scope(named(BackendConfigScopeManager.SCOPE_NAME)) {
        scoped { get<BackendRepository>().fetchBackendConfig() }
    }

    factory { get<BackendRepository>().backendConfig() }
}
