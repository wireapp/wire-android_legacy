package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT


interface UserApi {

    @GET("/self")
    fun getProfile(): Single<UserEntity>

    @PUT("/self/name")
    fun updateName(name: String): Completable

    @PUT("/self/handle")
    fun updateHandle(handle: String): Completable

    @PUT("/self/email")
    fun updateEmail(email: String): Completable

    @PUT("/self/phone")
    fun updatePhone(phone: String): Completable
}
