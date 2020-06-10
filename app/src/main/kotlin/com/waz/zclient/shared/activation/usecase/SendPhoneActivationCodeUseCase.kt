package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.activation.ActivationRepository

class SendPhoneActivationCodeUseCase(private val activationRepository: ActivationRepository) :
    UseCase<Unit, SendPhoneActivationCodeParams> {
    override suspend fun run(params: SendPhoneActivationCodeParams): Either<Failure, Unit> =
        activationRepository.sendPhoneActivationCode(params.phone)
            .fold({
                when (it) {
                    is Forbidden -> Either.Left(PhoneBlacklisted)
                    is Conflict -> Either.Left(PhoneInUse)
                    else -> Either.Left(it)
                }
            }) { Either.Right(it) }!!
}

data class SendPhoneActivationCodeParams(val phone: String)

object PhoneBlacklisted : SendPhoneActivationCodeFailure()
object PhoneInUse : SendPhoneActivationCodeFailure()

sealed class SendPhoneActivationCodeFailure : FeatureFailure()
