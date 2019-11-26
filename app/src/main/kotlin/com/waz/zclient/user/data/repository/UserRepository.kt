package com.waz.zclient.user.data.repository

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Single

interface UserRepository {

    fun getUserProfile(): Single<UserEntity>
}
