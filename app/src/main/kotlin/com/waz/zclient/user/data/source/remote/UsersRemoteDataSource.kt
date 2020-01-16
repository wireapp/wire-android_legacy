package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.user.data.source.remote.model.UserApi

class UsersRemoteDataSource(
    private val usersNetworkService: UsersNetworkService,
    override val networkHandler: NetworkHandler
) : ApiService() {

    suspend fun profileDetails(): Either<Failure, UserApi> =
        request { usersNetworkService.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Unit> =
        request { usersNetworkService.changeName(ChangeNameRequest(name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Unit> =
        request { usersNetworkService.changeHandle(ChangeHandleRequest(handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Unit> =
        request { usersNetworkService.changeEmail(ChangeEmailRequest(email)) }

    suspend fun changePhone(phone: String): Either<Failure, Unit> =
        request { usersNetworkService.changePhone(ChangePhoneRequest(phone)) }
}
