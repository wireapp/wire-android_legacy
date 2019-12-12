package com.waz.zclient.user.data.source

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.user.domain.model.User


interface UsersDataSource {
    suspend fun profile(): Either<Failure, User>
    suspend fun changeHandle(value: String): Either<Failure, Any>
    suspend fun changeEmail(value: String): Either<Failure, Any>
    suspend fun changePhone(value: String): Either<Failure, Any>
}
