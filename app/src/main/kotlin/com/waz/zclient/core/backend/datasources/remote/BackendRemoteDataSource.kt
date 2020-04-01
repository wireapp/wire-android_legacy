package com.waz.zclient.core.backend.datasources.remote

class BackendRemoteDataSource(private val backendApiService: BackendApiService) {

    suspend fun getCustomBackendConfig(url: String) =
        backendApiService.getCustomBackendConfig(url)
}
