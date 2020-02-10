package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.handle.UserHandleRepository

class CheckHandleExistsUseCase(private val handleRepository: UserHandleRepository) :
    UseCase<ValidateHandleSuccess, CheckHandleExistsParams>() {

    override suspend fun run(params: CheckHandleExistsParams): Either<Failure, ValidateHandleSuccess> =
        handleRepository.doesHandleExist(params.newHandle)
}

data class CheckHandleExistsParams(val newHandle: String)
