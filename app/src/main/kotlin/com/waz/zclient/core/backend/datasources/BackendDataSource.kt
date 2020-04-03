package com.waz.zclient.core.backend.datasources

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.core.backend.BackendItem
import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.di.BackendRemoteDataSourceProvider
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class BackendDataSource(
    private val remoteDataSourceProvider: BackendRemoteDataSourceProvider,
    private val localDataSource: BackendLocalDataSource,
    private val backendClient: BackendClient,
    private val backendMapper: BackendMapper
) : BackendRepository {

    private val remoteDataSource
        get() = remoteDataSourceProvider.backendRemoteDataSource()

    //TODO: detect if the config changed during app lifetime
    override fun configuredUrl(): String? = null

    //TODO: lazy access
    override fun backendConfig(): BackendItem =
        localDataSource.backendConfig().fold({
            backendClient.get(localDataSource.environment())
        }) {
            backendMapper.toBackendItem(it)
        }!!

    override suspend fun loadBackendConfig(url: String): Either<Failure, BackendItem> =
        remoteDataSource.getCustomBackendConfig(url).map {
            val backendItem = backendMapper.toBackendItem(it)
            localDataSource.updateBackendConfig(url, backendMapper.toPreference(backendItem))
            backendItem
        }
}
