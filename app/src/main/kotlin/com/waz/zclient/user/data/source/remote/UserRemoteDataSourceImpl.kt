package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserRemoteDataSourceImpl constructor(
    private val userApi: UserApi
) : UserRemoteDataSource {

    override fun getProfile(): Single<UserEntity> = userApi.getProfile()
    override fun updateName(name: String): Completable = userApi.updateName(name)
    override fun updateHandle(handle: String): Completable = userApi.updateHandle(handle)
    override fun updateEmail(email: String): Completable = userApi.updateEmail(email)
    override fun updatePhone(phone: String): Completable = userApi.updatePhone(phone)

}
