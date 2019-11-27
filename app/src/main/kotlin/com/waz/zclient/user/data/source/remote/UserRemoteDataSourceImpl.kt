package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.data.source.remote.Network
import com.waz.zclient.core.data.source.remote.Network.Companion.API_TOKEN
import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Single

class UserRemoteDataSourceImpl : UserRemoteDataSource {

    private val network = Network()

    override fun getUserProfile(): Single<UserEntity> = network.getUserApi().getUserProfile(API_TOKEN)

}
