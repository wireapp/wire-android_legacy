package com.waz.zclient.shared.assets

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface AssetsApi {

    @GET("/assets/v3/{assetId}")
    suspend fun publicAsset(@Path("assetId") assetId: String): Response<ResponseBody>
}
