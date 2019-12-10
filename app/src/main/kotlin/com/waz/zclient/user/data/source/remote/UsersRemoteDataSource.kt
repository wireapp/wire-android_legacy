package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.network.Network
import com.waz.zclient.storage.db.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UsersRemoteDataSource constructor(private val userApi: UsersApi = Network().userApi()) {

    fun profile(): Single<UserEntity> = userApi.profile()
    fun changeHandle(value: String): Completable = userApi.changeHandle(value)
    fun changeEmail(value: String): Completable = userApi.changeEmail(value)
    fun changePhone(value: String): Completable = userApi.changePhone(value)

}
