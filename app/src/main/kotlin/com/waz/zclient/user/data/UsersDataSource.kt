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

@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions")
//TODO get rid of this class eventually as it's too broad,
//It should be broken down into smaller Repositories
class UsersDataSource(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource,
    private val userMapper: UserMapper
) : UsersRepository {

    override suspend fun profileDetails(): Flow<User> = profileDetailsLocally()
        .catch {
            profileDetailsRemotely().onSuccess {
                runBlocking { saveUser() }
            }.map {
                runBlocking { emit(it) }
            }
        }

    private suspend fun profileDetailsRemotely() = usersRemoteDataSource.profileDetails()
        .map { userMapper.toUser(it) }

    private fun profileDetailsLocally(): Flow<User> = usersLocalDataSource.profileDetails()
        .map { userMapper.toUser(it) }

    private suspend fun saveUser(): suspend (User) -> Unit = {
        usersLocalDataSource.insertUser(userMapper.toUserDao(it))
    }

    override suspend fun changeName(name: String) = changeNameRemotely(name)
        .onSuccess { runBlocking { changeNameLocally(name) } }

    private suspend fun changeNameRemotely(name: String) = usersRemoteDataSource.changeName(name)

    private suspend fun changeNameLocally(name: String) = usersLocalDataSource.changeName(name)

    override suspend fun changeEmail(email: String) = changeEmailRemotely(email)
        .onSuccess { runBlocking { changeEmailLocally(email) } }

    private suspend fun changeEmailRemotely(email: String) = usersRemoteDataSource.changeEmail(email)

    private suspend fun changeEmailLocally(email: String) = usersLocalDataSource.changeEmail(email)
}
