package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT


interface UserApi {

    @GET("/self")
    fun getProfile(@Header("Authorization") token:String): Single<UserEntity>

    @PUT("/self/name")
    fun updateName(@Header("Authorization") token:String, name:String): Completable

    @PUT("/self/handle")
    fun updateHandle(@Header("Authorization") token:String, handle:String): Completable

    @PUT("/self/email")
    fun updateEmail(@Header("Authorization") token:String, email:String): Completable

    @PUT("/self/phone")
    fun updatePhone(@Header("Authorization") token:String, phone:String): Completable
}
