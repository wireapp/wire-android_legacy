package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.GET
import retrofit2.http.PUT


interface UsersNetworkService {

    @GET("/self")
    suspend fun profile(): Response<UserApi>

    @PUT("/self")
    suspend fun changeName(@Body userApi: UserApi): Response<Void>


    @PUT("/self/handle")
    suspend fun changeHandle(@Field("handle") value: String): Response<Void>

    @PUT("/self/email")
    suspend fun changeEmail(@Field("email") value: String): Response<Void>

    @PUT("/self/phone")
    suspend fun changePhone(@Field("phone") value: String): Response<Void>
}
