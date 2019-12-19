package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.network.Network

class UsersNetwork : Network() {

    fun usersApi(): UsersNetworkService {
        return retrofit.create(UsersNetworkService::class.java)
    }
}
