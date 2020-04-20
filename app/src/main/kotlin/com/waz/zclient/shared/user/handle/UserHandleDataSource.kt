package com.waz.zclient.shared.user.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.user.datasources.local.UsersLocalDataSource
import com.waz.zclient.shared.user.datasources.remote.UsersRemoteDataSource
import kotlinx.coroutines.runBlocking

class UserHandleDataSource(
    private val usersRemoteDataSource: UsersRemoteDataSource,
    private val usersLocalDataSource: UsersLocalDataSource
) : UserHandleRepository {

    override suspend fun changeHandle(handle: String) = changeHandleRemotely(handle)
        .onSuccess { runBlocking { changeHandleLocally(handle) } }

    private suspend fun changeHandleRemotely(handle: String) =
        usersRemoteDataSource.changeHandle(handle)

    private suspend fun changeHandleLocally(handle: String) =
        usersLocalDataSource.changeHandle(handle)

    override suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess> =
        usersRemoteDataSource.doesHandleExist(newHandle)
}
