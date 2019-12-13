package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.storage.db.model.UserEntity

class UsersRemoteDataSource constructor(private val userApi: UsersApi = UsersNetwork().usersApi()) {

    suspend fun profile(): Either<Failure, UserEntity> = requestApi { userApi.profile() }

    suspend fun changeHandle(value: String): Either<Failure, Any> = requestApi { userApi.changeHandle(value) }

    suspend fun changeEmail(value: String): Either<Failure, Any> = requestApi { userApi.changeEmail(value) }

    suspend fun changePhone(value: String): Either<Failure, Any> = requestApi { userApi.changePhone(value) }
}
