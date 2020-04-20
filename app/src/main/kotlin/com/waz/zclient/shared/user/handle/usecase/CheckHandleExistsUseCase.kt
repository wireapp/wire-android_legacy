package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.handle.UserHandleRepository
import com.waz.zclient.shared.user.handle.ValidateHandleSuccess

class CheckHandleExistsUseCase(private val handleRepository: UserHandleRepository) :
    UseCase<ValidateHandleSuccess, CheckHandleExistsParams>() {

    override suspend fun run(params: CheckHandleExistsParams): Either<Failure, ValidateHandleSuccess> =
        handleRepository.doesHandleExist(params.newHandle)
}

data class CheckHandleExistsParams(val newHandle: String)
