package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PUT


interface UserApi {

    @GET("/self")
    fun getProfile(): Single<UserEntity>

    @FormUrlEncoded
    @PUT("/self/name")
    fun updateName(@Field("name") name: String): Completable

    @FormUrlEncoded
    @PUT("/self/handle")
    fun updateHandle(@Field("handle") handle: String): Completable

    @FormUrlEncoded
    @PUT("/self/email")
    fun updateEmail(@Field("email") email: String): Completable

    @FormUrlEncoded
    @PUT("/self/phone")
    fun updatePhone(@Field("phone") phone: String): Completable
}
