package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.activation.ActivationRepository

class ActivateEmailUseCase(
    private val activationRepository: ActivationRepository
) : UseCase<Unit, ActivateEmailParams> {
    override suspend fun run(params: ActivateEmailParams): Either<Failure, Unit> =
        activationRepository.activateEmail(params.email, params.code)
            .fold({
                when (it) {
                    is NotFound -> Either.Left(InvalidEmailCode)
                    else -> Either.Left(it)
                }
            }) { Either.Right(it) }!!
}

data class ActivateEmailParams(val email: String, val code: String)

object InvalidEmailCode : ActivateEmailFailure()

sealed class ActivateEmailFailure : FeatureFailure()
