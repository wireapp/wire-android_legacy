package com.waz.zclient.core.network.api.token

import retrofit2.Call
import retrofit2.http.HeaderMap
import retrofit2.http.POST

interface TokenApi {

    @POST("/access")
    fun access(@HeaderMap headers: Map<String, String>): Call<AccessTokenResponse>
}
