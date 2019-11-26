package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Single

interface UserRemoteDataSource {

    fun getUserProfile(): Single<UserEntity>
}
