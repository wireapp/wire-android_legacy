package com.waz.zclient.user.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.handle.UserHandleRepository

class ChangeHandleUseCase(private val handleRepository: UserHandleRepository) :
    UseCase<Any, ChangeHandleParams>() {

    override suspend fun run(params: ChangeHandleParams): Either<Failure, Any> =
        handleRepository.changeHandle(params.newHandle)
}

data class ChangeHandleParams(val newHandle: String)
