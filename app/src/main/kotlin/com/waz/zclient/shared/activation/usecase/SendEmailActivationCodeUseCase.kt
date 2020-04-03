package com.waz.zclient.shared.activation.usecase

import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.activation.ActivationRepository

class SendEmailActivationCodeUseCase(private val activationRepository: ActivationRepository) :
    UseCase<Unit, SendEmailActivationCodeParams>() {
    override suspend fun run(params: SendEmailActivationCodeParams): Either<Failure, Unit> =
        activationRepository.sendEmailActivationCode(params.email).onFailure {
            when (it) {
                is BadRequest -> Either.Left(InvalidEmail)
                is Forbidden -> Either.Left(EmailBlackListed)
                is Conflict -> Either.Left(EmailInUse)
                else -> Either.Left(UnknownError)
            }
        }
}

data class SendEmailActivationCodeParams(val email: String)

object InvalidEmail : SendActivationCodeFailure()
object EmailBlackListed : SendActivationCodeFailure()
object EmailInUse : SendActivationCodeFailure()
object UnknownError : SendActivationCodeFailure()

sealed class SendActivationCodeFailure : FeatureFailure()
