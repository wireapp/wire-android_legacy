package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.activation.ActivationRepository

class SendEmailActivationCodeUseCase(private val activationRepository: ActivationRepository) :
    UseCase<Unit, SendEmailActivationCodeParams>() {
    override suspend fun run(params: SendEmailActivationCodeParams): Either<Failure, Unit> =
        activationRepository.sendEmailActivationCode(params.email)
            .fold({
                when (it) {
                    is Forbidden -> Either.Left(EmailBlacklisted)
                    is Conflict -> Either.Left(EmailInUse)
                    else -> Either.Left(it)
                }
            }) { Either.Right(it) }!!
}

data class SendEmailActivationCodeParams(val email: String)

object EmailBlacklisted : SendActivationCodeFailure()
object EmailInUse : SendActivationCodeFailure()

sealed class SendActivationCodeFailure : FeatureFailure()
