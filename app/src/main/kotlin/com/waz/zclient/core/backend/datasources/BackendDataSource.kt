package com.waz.zclient.core.backend.datasources

import com.waz.zclient.core.backend.BackendClient
import com.waz.zclient.core.backend.BackendItem
import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.di.BackendConfigScopeManager
import com.waz.zclient.core.backend.di.BackendRemoteDataSourceProvider
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

class BackendDataSource(
    private val remoteDataSourceProvider: BackendRemoteDataSourceProvider,
    private val localDataSource: BackendLocalDataSource,
    private val backendClient: BackendClient,
    private val backendMapper: BackendMapper,
    private val scopeManager: BackendConfigScopeManager
) : BackendRepository {

    private val remoteDataSource
        get() = remoteDataSourceProvider.backendRemoteDataSource()

    override fun configuredUrl(): String? = if (scopeManager.isDefaultConfig()) null else backendConfig().baseUrl

    override fun backendConfig(): BackendItem = scopeManager.backendItem()

    override fun fetchBackendConfig(): BackendItem =
        localDataSource.backendConfig().fold({
            backendClient.get(localDataSource.environment())
        }) {
            backendMapper.toBackendItem(it)
        }!!

    override suspend fun loadBackendConfig(url: String): Either<Failure, BackendItem> =
        remoteDataSource.getCustomBackendConfig(url).map {
            val backendItem = backendMapper.toBackendItem(it)
            localDataSource.updateBackendConfig(url, backendMapper.toPreference(backendItem))
            scopeManager.onConfigChanged(backendItem.environment)
            backendItem
        }
}
