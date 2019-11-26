package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Header


interface UserApi {

    @GET("/self")
    fun getUserProfile(): Single<UserEntity>
}
