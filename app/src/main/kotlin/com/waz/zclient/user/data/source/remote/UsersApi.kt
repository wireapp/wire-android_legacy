package com.waz.zclient.user.data.source.remote

import com.waz.zclient.storage.db.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PUT


interface UsersApi {

    @GET("/self")
    fun profile(): Single<UserEntity>

    @FormUrlEncoded
    @PUT("/self/handle")
    fun changeHandle(@Field("handle") value: String): Completable

    @FormUrlEncoded
    @PUT("/self/email")
    fun changeEmail(@Field("email") value: String): Completable

    @FormUrlEncoded
    @PUT("/self/phone")
    fun changePhone(@Field("phone") value: String): Completable
}
