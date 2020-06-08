package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.activation.ActivationRepository

class ActivatePhoneUseCase(
    private val activationRepository: ActivationRepository
) : UseCase<Unit, ActivatePhoneParams> {
    override suspend fun run(params: ActivatePhoneParams): Either<Failure, Unit> =
        activationRepository.activatePhone(params.phone, params.code)
            .fold({
                if (it is NotFound) Either.Left(InvalidSmsCode)
                else Either.Left(it)
            }) { Either.Right(it) }!!
}

data class ActivatePhoneParams(val phone: String, val code: String)

object InvalidSmsCode : ActivatePhoneFailure()

sealed class ActivatePhoneFailure : FeatureFailure()
