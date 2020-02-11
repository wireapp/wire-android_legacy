package com.waz.zclient.user.data.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleSuccess

interface UserHandleRepository {
    suspend fun changeHandle(handle: String): Either<Failure, Any>
    suspend fun doesHandleExist(newHandle: String): Either<Failure, ValidateHandleSuccess>
}
