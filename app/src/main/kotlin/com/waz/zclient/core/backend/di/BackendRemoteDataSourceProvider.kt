package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import org.koin.core.KoinComponent
import org.koin.core.get

/**
 * Dependency provider that enables lazy loading of [BackendRemoteDataSource], letting network layer initialize itself
 * before creating the dependency.
 */
class BackendRemoteDataSourceProvider : KoinComponent {
    fun backendRemoteDataSource(): BackendRemoteDataSource = get()
}
