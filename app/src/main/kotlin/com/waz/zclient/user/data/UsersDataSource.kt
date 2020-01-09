package com.waz.zclient.user.data

import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.user.data.mapper.UserMapper
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class UsersDataSource constructor(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource,
    private val userMapper: UserMapper) : UsersRepository {

    @ExperimentalCoroutinesApi
    override suspend fun profileDetails(): Flow<User> = profileDetailsLocally()
        .catch {
            profileDetailsRemotely().onSuccess {
                runBlocking { saveUser() }
            }.map {
                runBlocking { emit(it) }
            }
        }

    override suspend fun changeName(value: String) = changeNameRemotely(value)
        .onSuccess { runBlocking { changeNameLocally(value) } }

    override suspend fun changeHandle(value: String) = usersRemoteDataSource.changeHandle(value)

    override suspend fun changeEmail(value: String) = usersRemoteDataSource.changeEmail(value)

    override suspend fun changePhone(value: String) = usersRemoteDataSource.changePhone(value)

    private suspend fun profileDetailsRemotely() = usersRemoteDataSource.profileDetails()
        .map { userMapper.toUser(it) }

    private fun profileDetailsLocally(): Flow<User> = usersLocalDataSource.profileDetails()
        .map { userMapper.toUser(it) }

    private suspend fun saveUser(): suspend (User) -> Unit = { usersLocalDataSource.insertUser(userMapper.toUserDao(it)) }

    private suspend fun changeNameRemotely(value: String) = usersRemoteDataSource.changeName(value)

    private suspend fun changeNameLocally(value: String) = usersLocalDataSource.changeName(value)

}
