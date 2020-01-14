package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT


interface UsersNetworkService {

    @GET(BASE)
    suspend fun profileDetails(): Response<UserApi>

    @PUT("$BASE$NAME")
    suspend fun changeName(@Body name: JSONObject): Response<Unit>

    @PUT("$BASE$HANDLE")
    suspend fun changeHandle(@Body handle: JSONObject): Response<Unit>

    @PUT("$BASE$EMAIL")
    suspend fun changeEmail(@Body email: JSONObject): Response<Unit>

    @PUT("$BASE$PHONE")
    suspend fun changePhone(@Body phone: JSONObject): Response<Unit>

    companion object {
        private const val BASE = "/self"
        private const val PHONE = "/phone"
        private const val EMAIL = "/email"
        private const val HANDLE = "/handle"
        private const val NAME = "/name"
    }
}
