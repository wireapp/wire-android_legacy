package com.waz.zclient.core.backend.datasources.remote

import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class BackendApiService(
    private val backendApi: BackendApi,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun getCustomBackendConfig(url: String) =
        request { backendApi.getCustomBackendConfig(url) }
}
