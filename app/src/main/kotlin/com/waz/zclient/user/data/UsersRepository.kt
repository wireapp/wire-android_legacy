package com.waz.zclient.user.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.user.domain.model.User


interface UsersRepository {
    suspend fun profile(): Either<Failure, User>
    suspend fun changeHandle(value: String): Either<Failure, Any>
    suspend fun changeEmail(value: String): Either<Failure, Any>
    suspend fun changePhone(value: String): Either<Failure, Any>
}
