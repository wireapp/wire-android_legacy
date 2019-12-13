package com.waz.zclient.user.data


import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersNetwork
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User


class UsersDataSource constructor(private val usersRemoteDataSource: UsersRemoteDataSource = UsersRemoteDataSource(),
                                  private val usersLocalDataSource: UsersLocalDataSource = UsersLocalDataSource()) : UsersRepository {

    override suspend fun profile(): Either<Failure, User> = resultEither(
        mainRequest = { usersLocalDataSource.profile() },
        fallbackRequest = { usersRemoteDataSource.profile() },
        saveToDatabase = { usersLocalDataSource.add(it) }).map { it.toUser() }

    override suspend fun changeHandle(value: String): Either<Failure, Any> = requestData {
        usersRemoteDataSource.changeHandle(value)
    }

    override suspend fun changeEmail(value: String): Either<Failure, Any> = requestData {
        usersRemoteDataSource.changeEmail(value)
    }

    override suspend fun changePhone(value: String): Either<Failure, Any> = requestData {
        usersRemoteDataSource.changePhone(value)
    }

    companion object {

        @Volatile
        private var usersRepository: UsersRepository? = null

        fun getInstance(remoteDataSource: UsersRemoteDataSource = UsersRemoteDataSource(UsersNetwork().usersApi()),
                        localDataSource: UsersLocalDataSource = UsersLocalDataSource()): UsersRepository =
            usersRepository
                ?: synchronized(this) {
                    usersRepository
                        ?: UsersDataSource(remoteDataSource, localDataSource).also {
                            usersRepository = it
                        }
                }

        fun destroyInstance() {
            usersRepository = null
        }
    }

}
