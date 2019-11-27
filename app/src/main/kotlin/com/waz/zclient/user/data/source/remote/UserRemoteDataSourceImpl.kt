package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.data.source.remote.Network
import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserRemoteDataSourceImpl : UserRemoteDataSource {

    private val network = Network()

    override fun getProfile(): Single<UserEntity> = network.getUserApi().getProfile()
    override fun updateName(name: String): Completable = network.getUserApi().updateName(name)
    override fun updateHandle(handle: String): Completable = network.getUserApi().updateHandle(handle)
    override fun updateEmail(email: String): Completable = network.getUserApi().updateEmail(email)
    override fun updatePhone(phone: String): Completable = network.getUserApi().updatePhone(phone)

}
