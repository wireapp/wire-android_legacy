package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserRemoteDataSourceImpl constructor(
    private val userApi: UserApi
) : UserRemoteDataSource {

    override fun profile(): Single<UserEntity> = userApi.profile()
    override fun name(name: String): Completable = userApi.name(name)
    override fun handle(handle: String): Completable = userApi.handle(handle)
    override fun email(email: String): Completable = userApi.email(email)
    override fun phone(phone: String): Completable = userApi.phone(phone)

}
