package com.waz.zclient.core.backend

import com.waz.zclient.core.backend.datasources.BackendRepository
import com.waz.zclient.core.backend.datasources.local.BackendPrefsDataSource
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefResponse
import com.waz.zclient.core.backend.datasources.remote.BackendRemoteDataSource
import com.waz.zclient.core.backend.mapper.BackendMapper
import com.waz.zclient.core.backend.usecase.CustomBackend
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.FallbackOnFailure
import com.waz.zclient.core.functional.map
import kotlinx.coroutines.runBlocking

class BackendDataSource(
    private val remoteDataSource: BackendRemoteDataSource,
    private val prefsDataSource: BackendPrefsDataSource,
    private val backendMapper: BackendMapper
) : BackendRepository {

    override suspend fun getCustomBackendConfig(url: String): Either<Failure, CustomBackend> =
        FallbackOnFailure(
            { getCustomBackendConfigLocally() },
            { getCustomBackendConfigRemotely(url) }
        ).finally {
            updateCustomBackendConfigLocally(url, backendMapper.toCustomPrefBackend(it))
        }.execute()

    private fun updateCustomBackendConfigLocally(
        configUrl: String,
        backendPrefResponse: CustomBackendPrefResponse
    ) = prefsDataSource.updateCustomBackendConfig(configUrl, backendPrefResponse)

    private fun getCustomBackendConfigRemotely(url: String) =
        runBlocking {
            remoteDataSource.getCustomBackendConfig(url).map {
                backendMapper.toCustomBackend(it)
            }
        }

    private fun getCustomBackendConfigLocally() =
        runBlocking {
            prefsDataSource.getCustomBackendConfig().map {
                backendMapper.toCustomBackend(it)
            }
        }

}
