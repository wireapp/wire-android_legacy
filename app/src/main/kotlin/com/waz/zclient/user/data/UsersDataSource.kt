package com.waz.zclient.user.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.user.data.mapper.UserMapper
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

class UsersDataSource constructor(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource,
    private val userMapper: UserMapper) : UsersRepository {


    @ExperimentalCoroutinesApi
    override suspend fun profile(): Flow<User> = profileLocally().catch {
        emitAll(
            profileRemotely().onCompletion {
               runBlocking {  saveUser()}
            })
    }

    @ExperimentalCoroutinesApi
    override suspend fun changeName(value: String): Flow<Void> =
        changeNameRemotely(value).onCompletion{ runBlocking { changeNameLocally(value)  } }

    override suspend fun changeHandle(value: String): Either<Failure, Any> =
        usersRemoteDataSource.changeHandle(value)

    override suspend fun changeEmail(value: String): Either<Failure, Any> =
        usersRemoteDataSource.changeEmail(value)

    override suspend fun changePhone(value: String): Either<Failure, Any> =
        usersRemoteDataSource.changePhone(value)

    private suspend fun profileRemotely(): Flow<User> =
        usersRemoteDataSource.profile().map { userMapper.toUser(it) }

    private suspend fun profileLocally(): Flow<User> =
        usersLocalDataSource.profile().map { userMapper.toUser(it) }

    private suspend fun saveUser(): suspend (User) -> Unit = { usersLocalDataSource.add(userMapper.toUserDao(it)) }

    private suspend fun changeNameRemotely(value: String): Flow<Void> =
        usersRemoteDataSource.changeName(value)

    private suspend fun changeNameLocally(value: String) =
        usersLocalDataSource.changeName(value)

}
