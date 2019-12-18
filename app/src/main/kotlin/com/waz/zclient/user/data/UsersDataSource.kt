package com.waz.zclient.user.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.user.data.mapper.UserMapper
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User

class UsersDataSource constructor(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource,
    private val userMapper: UserMapper) : UsersRepository {

    override suspend fun profile(): Either<Failure, User> =
        resultEither(profileLocal(), profileRemote(), saveUser())

    override suspend fun changeHandle(value: String): Either<Failure, Any> =
        requestData { usersRemoteDataSource.changeHandle(value) }

    override suspend fun changeEmail(value: String): Either<Failure, Any> =
        requestData { usersRemoteDataSource.changeEmail(value) }

    override suspend fun changePhone(value: String): Either<Failure, Any> =
        requestData { usersRemoteDataSource.changePhone(value) }

    private fun profileRemote(): suspend () -> Either<Failure, User> =
        { usersRemoteDataSource.profile().map { userMapper.toUser(it) } }

    private fun profileLocal(): suspend () -> Either<Failure, User> =
        { usersLocalDataSource.profile().map { userMapper.toUser(it) } }

    private fun saveUser(): suspend (User) -> Unit = { usersLocalDataSource.add(userMapper.toUserDao(it)) }

}
