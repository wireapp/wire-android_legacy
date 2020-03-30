package com.waz.zclient.core.backend.datasources

import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.backend.CustomBackend
import com.waz.zclient.core.backend.datasources.local.BackendLocalDataSource
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.fallback
import com.waz.zclient.core.functional.map

class BackendDataSource(
    private val remoteDataSource: BackendRemoteDataSource,
    private val localDataSource: BackendLocalDataSource,
    private val backendMapper: BackendMapper
) : BackendRepository {

    override suspend fun getCustomBackendConfig(url: String): Either<Failure, CustomBackend> =
        getCustomBackendConfigLocally()
            .fallback { getCustomBackendConfigRemotely(url) }
            .finally { updateCustomBackendConfigLocally(url, it) }
            .execute()

    private fun updateCustomBackendConfigLocally(configUrl: String, customBackend: CustomBackend) {
        localDataSource.updateCustomBackendConfig(configUrl, backendMapper.toCustomPrefBackend(customBackend))
    }

    private suspend fun getCustomBackendConfigRemotely(url: String) =
        remoteDataSource.getCustomBackendConfig(url).map {
            backendMapper.toCustomBackend(it)
        }

    private fun getCustomBackendConfigLocally(): suspend () -> Either<Failure, CustomBackend> = {
        localDataSource.getCustomBackendConfig().map {
            backendMapper.toCustomBackend(it)
        }
    }
}
