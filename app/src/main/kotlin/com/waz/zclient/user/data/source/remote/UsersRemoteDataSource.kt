package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.toFlow
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.user.data.source.remote.model.UserApi
import kotlinx.coroutines.flow.Flow

class UsersRemoteDataSource constructor(private val usersNetworkService: UsersNetworkService) {

    suspend fun profileDetails(): Flow<UserApi> = usersNetworkService.profileDetails().toFlow()

    suspend fun changeName(value: String): Flow<Void> =  usersNetworkService.changeName(UserApi(name=value)).toFlow()

    suspend fun changeHandle(value: String): Either<Failure, Any> = requestApi { usersNetworkService.changeHandle(value) }

    suspend fun changeEmail(value: String): Either<Failure, Any> = requestApi { usersNetworkService.changeEmail(value) }

    suspend fun changePhone(value: String): Either<Failure, Any> = requestApi { usersNetworkService.changePhone(value) }

}
