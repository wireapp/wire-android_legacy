package com.waz.zclient.user.data


import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.core.requests.map
import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersNetwork
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User

class UsersRepository constructor(private val usersRemoteDataSource: UsersRemoteDataSource = UsersRemoteDataSource(),
                                  private val usersLocalDataSource: UsersLocalDataSource = UsersLocalDataSource()) : UsersDataSource {

    override suspend fun profile(): Either<Failure, User> = resultEither(
        databaseRequest = { usersLocalDataSource.profile() },
        networkRequest = { usersLocalDataSource.profile() },
        saveCallRequest = { usersLocalDataSource.add(it) }).map {
        it.toUser()
    }

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
            usersRepository ?: synchronized(this) {
                usersRepository ?: UsersRepository(remoteDataSource, localDataSource).also {
                    usersRepository = it
                }
            }

        fun destroyInstance() {
            usersRepository = null
        }
    }

}
