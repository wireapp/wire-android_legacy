package com.waz.zclient.user.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleSuccess
import kotlinx.coroutines.flow.Flow

interface UsersRepository {
    suspend fun profileDetails(): Flow<User>
    suspend fun changeName(name: String): Either<Failure, Any>
    suspend fun changeHandle(handle: String): Either<Failure, Any>
    suspend fun changeEmail(email: String): Either<Failure, Any>
    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess>
}
