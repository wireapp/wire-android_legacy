package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.user.data.source.remote.model.UserApi

class UsersRemoteDataSource constructor(private val usersNetworkService: UsersNetworkService) {

    suspend fun profileDetails(): Either<Failure, UserApi> = requestApi { usersNetworkService.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Any> = requestApi { usersNetworkService.changeName(UserApi(name = name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Any> = requestApi { usersNetworkService.changeHandle(UserApi(handle = handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Any> = requestApi { usersNetworkService.changeEmail(UserApi(email = email)) }

    suspend fun changePhone(phone: String): Either<Failure, Any> = requestApi { usersNetworkService.changePhone(UserApi(phone = phone)) }

}
