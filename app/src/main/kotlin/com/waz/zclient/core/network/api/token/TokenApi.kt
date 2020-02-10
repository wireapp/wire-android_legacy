package com.waz.zclient.core.network.api.token

import retrofit2.Response
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface TokenApi {

    @POST("/access")
    suspend fun access(@HeaderMap headers: Map<String, String>): Response<AccessTokenResponse>

    @POST("/access/logout")
    suspend fun logout(@HeaderMap headers: Map<String, String>): Response<Unit>
}
