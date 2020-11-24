package com.waz.zclient.shared.user.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class UsersRemoteDataSource(
    private val usersApi: UsersApi,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun profileDetails(): Either<Failure, UserResponse> =
        request { usersApi.profileDetails() }
}
