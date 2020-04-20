package com.waz.zclient.core.backend.datasources.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface BackendApi {

    @GET
    suspend fun getCustomBackendConfig(@Url url: String): Response<CustomBackendResponse>
}
