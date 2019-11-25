package com.waz.zclient.settings.data.source.remote

import com.waz.zclient.settings.data.model.UserEntity
import io.reactivex.Single

interface UserRemoteDataSource {

    fun getUserProfile(): Single<UserEntity>
}
