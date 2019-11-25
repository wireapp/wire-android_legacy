package com.waz.zclient.settings.data.repository

import com.waz.zclient.settings.data.model.UserEntity
import io.reactivex.Single

interface UserRepository {

    fun getUserProfile(): Single<UserEntity>
}
