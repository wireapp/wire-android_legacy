package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.network.Network
import com.waz.zclient.storage.db.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserRemoteDataSourceImpl(private val userApi: UserApi = Network().userApi()) : UserRemoteDataSource {

    override fun profile(): Single<UserEntity> = userApi.profile()
    override fun name(name: String): Completable = userApi.name(name)
    override fun handle(handle: String): Completable = userApi.handle(handle)
    override fun email(email: String): Completable = userApi.email(email)
    override fun phone(phone: String): Completable = userApi.phone(phone)

}
