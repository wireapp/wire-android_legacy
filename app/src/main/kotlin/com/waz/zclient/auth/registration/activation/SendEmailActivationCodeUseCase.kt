package com.waz.zclient.auth.registration.activation

import com.waz.zclient.core.exception.*
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.usecase.UseCase

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

sealed class SendActivationCodeFailure : UseCaseFailure()
