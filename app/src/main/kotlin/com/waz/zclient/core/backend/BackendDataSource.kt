package com.waz.zclient.core.backend

import com.waz.zclient.core.backend.datasources.BackendRepository
import com.waz.zclient.core.backend.datasources.local.BackendPrefsDataSource
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefResponse
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.backend.usecase.CustomBackend
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.fallback
import com.waz.zclient.core.functional.map

class BackendDataSource(
    private val remoteDataSource: BackendRemoteDataSource,
    private val prefsDataSource: BackendPrefsDataSource,
    private val backendMapper: BackendMapper
) : BackendRepository {

    override suspend fun getCustomBackendConfig(url: String): Either<Failure, CustomBackend> =
        getCustomBackendConfigLocally()
            .fallback { getCustomBackendConfigRemotely(url) }
            .finally { updateCustomBackendConfigLocally(url, backendMapper.toCustomPrefBackend(it)) }
            .execute()

    private fun updateCustomBackendConfigLocally(
        configUrl: String,
        backendPrefResponse: CustomBackendPrefResponse
    ) = prefsDataSource.updateCustomBackendConfig(configUrl, backendPrefResponse)

    private suspend fun getCustomBackendConfigRemotely(url: String) =
        remoteDataSource.getCustomBackendConfig(url).map {
            backendMapper.toCustomBackend(it)
        }

    private fun getCustomBackendConfigLocally(): suspend () -> Either<Failure, CustomBackend> = {
        prefsDataSource.getCustomBackendConfig().map {
            backendMapper.toCustomBackend(it)
        }
    }
}
