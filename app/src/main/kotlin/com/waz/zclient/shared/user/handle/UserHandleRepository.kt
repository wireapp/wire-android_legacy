package com.waz.zclient.shared.user.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface UserHandleRepository {
    suspend fun changeHandle(handle: String): Either<Failure, Any>
    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess>
}
