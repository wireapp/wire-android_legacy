package com.waz.zclient.user.data

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.requestData
import com.waz.zclient.core.network.resultEither
import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersNetwork
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.user.domain.model.User


interface UsersRepository {
    suspend fun profile(): Either<Failure, User>
    suspend fun changeHandle(value: String): Either<Failure, Any>
    suspend fun changeEmail(value: String): Either<Failure, Any>
    suspend fun changePhone(value: String): Either<Failure, Any>
}
