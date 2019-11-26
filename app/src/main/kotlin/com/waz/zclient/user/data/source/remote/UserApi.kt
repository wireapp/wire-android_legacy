package com.waz.zclient.settings.data.source.remote

import com.waz.zclient.settings.data.model.UserEntity
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Header


interface UserApi {

    @GET("/self")
    fun getUserProfile(@Header("Authorization") token:String): Single<UserEntity>
}
