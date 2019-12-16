package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PUT


interface UsersNetworkService {

    @GET("/self")
    suspend fun profile(): Response<UserApi>

    @FormUrlEncoded
    @PUT("/self/handle")
    suspend fun changeHandle(@Field("handle") value: String): Response<Any>

    @FormUrlEncoded
    @PUT("/self/email")
    suspend fun changeEmail(@Field("email") value: String): Response<Any>

    @FormUrlEncoded
    @PUT("/self/phone")
    suspend fun changePhone(@Field("phone") value: String): Response<Any>
}
