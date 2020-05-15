package com.waz.zclient.shared.user

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.flow.Flow

interface UsersRepository {
    suspend fun profileDetails(): Flow<User>
    suspend fun changeName(name: String): Either<Failure, Any>
    suspend fun changeEmail(email: String): Either<Failure, Any>
    fun currentUserId(): String
    fun setCurrentUserId(userId: String)
}
