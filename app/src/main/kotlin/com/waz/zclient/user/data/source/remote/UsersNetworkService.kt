package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT


interface UsersNetworkService {

    @GET(BASE)
    suspend fun profileDetails(): Response<UserApi>

    @PUT("$BASE$NAME")
    suspend fun changeName(@Body user: UserApi): Response<Unit>

    @PUT("$BASE$HANDLE")
    suspend fun changeHandle(@Body user: UserApi): Response<Unit>

    @PUT("$BASE$EMAIL")
    suspend fun changeEmail(@Body user: UserApi): Response<Unit>

    @PUT("$BASE$PHONE")
    suspend fun changePhone(@Body user: UserApi): Response<Unit>


    companion object {
        private const val BASE = "/self"
        private const val PHONE = "/phone"
        private const val EMAIL = "/email"
        private const val HANDLE = "/handle"
        private const val NAME = "/name"
    }
}
